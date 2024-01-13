package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import it.unibo.collektive.Collektive
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MuxTest : StringSpec({

    // device ids
    val id0 = IntId(0)
    val id1 = IntId(1)

    val alwaysTrue: (StateFlow<Field<Boolean>>) -> StateFlow<Field<Boolean>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> true }
        }
    }

    val alwaysFalse: (StateFlow<Field<Boolean>>) -> StateFlow<Field<Boolean>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> false }
        }
    }

    "Branch with a reactive condition should work" {
        runBlocking {
            val channel0: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val reactiveBoolean = MutableStateFlow(true)

            val aggregateResult0: ReactiveAggregateResult<Field<Boolean>> = Collektive.aggregate(id0, channel0) {
                mux(
                    { reactiveBoolean },
                    { exchange(true, alwaysTrue) },
                    { exchange(false, alwaysFalse) },
                )
            }

            val aggregateResult1: ReactiveAggregateResult<Field<Boolean>> = Collektive.aggregate(id1, channel1) {
                mux(
                    { reactiveBoolean },
                    { exchange(true, alwaysTrue) },
                    { exchange(false, alwaysFalse) },
                )
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(
                    mapOf(
                        aggregateResult0 to channel0,
                        aggregateResult1 to channel1,
                    )
                )
            }
            reactiveBoolean.update { false }
            delay(100)
            job.cancelAndJoin()
        }
    }

    "Devices should not be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val reactiveBoolean0 = MutableStateFlow(true)
            val reactiveBoolean1 = MutableStateFlow(false)

            val aggregateResult0: ReactiveAggregateResult<Field<Boolean>> = Collektive.aggregate(id0, channel0) {
                mux(
                    { reactiveBoolean0 },
                    { exchange(true, alwaysTrue) },
                    { exchange(false, alwaysFalse) },
                )
            }

            val aggregateResult1: ReactiveAggregateResult<Field<Boolean>> = Collektive.aggregate(id1, channel1) {
                mux(
                    { reactiveBoolean1 },
                    { exchange(true, alwaysTrue) },
                    { exchange(false, alwaysFalse) },
                )
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(
                    mapOf(
                        aggregateResult0 to channel0,
                        aggregateResult1 to channel1,
                    )
                )
            }
            delay(100)
            job.cancelAndJoin()
        }
    }
})
