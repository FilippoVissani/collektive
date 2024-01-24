package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import it.unibo.collektive.Collektive
import it.unibo.collektive.IntId
import it.unibo.collektive.aggregate.branch
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BranchTest : StringSpec({

    // device ids
    val id0 = IntId(0)
    val id1 = IntId(1)

    val trueFunction: (StateFlow<Field<String>>) -> StateFlow<Field<String>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> "trueBranch" }
        }
    }

    val falseFunction: (StateFlow<Field<String>>) -> StateFlow<Field<String>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> "falseBranch" }
        }
    }

    "Test" {
        runBlocking {
            val channel0: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val reactiveBoolean0 = MutableStateFlow(true)
            val reactiveBoolean1 = MutableStateFlow(false)

            val aggregateResult0 = Collektive.aggregate(id0, channel0) {
                reactiveBoolean0.branch(exchange("initial", trueFunction), exchange("initial", falseFunction))
            }

            val aggregateResult1 = Collektive.aggregate(id1, channel1) {
                reactiveBoolean1.branch(exchange("initial", trueFunction), exchange("initial", falseFunction))
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