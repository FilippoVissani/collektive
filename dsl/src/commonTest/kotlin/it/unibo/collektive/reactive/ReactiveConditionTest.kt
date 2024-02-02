package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import it.unibo.collektive.Collektive
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.proactive.networking.InboundMessage
import it.unibo.collektive.reactive.flow.extensions.combineStates
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReactiveConditionTest : StringSpec({

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

    "Final result should change if the condition changes" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val reactiveBoolean = MutableStateFlow(true)

            val aggregateResult0 = Collektive.aggregate(id0, channel0) {
                combineStates(reactiveBoolean, exchange("initial", trueFunction), exchange("initial", falseFunction)) { c, t, e ->
                    if (c) t else e
                }
            }

            val aggregateResult1 = Collektive.aggregate(id1, channel1) {
                combineStates(reactiveBoolean, exchange("initial", trueFunction), exchange("initial", falseFunction)) { c, t, e ->
                    if (c) t else e
                }
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

    "Devices with different conditions should be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val reactiveBoolean0 = MutableStateFlow(true)
            val reactiveBoolean1 = MutableStateFlow(false)

            val aggregateResult0 = Collektive.aggregate(id0, channel0) {
                combineStates(reactiveBoolean0, exchange("initial", trueFunction), exchange("initial", falseFunction)) { c, t, e ->
                    if (c) t else e
                }
            }

            val aggregateResult1 = Collektive.aggregate(id1, channel1) {
                combineStates(reactiveBoolean1, exchange("initial", trueFunction), exchange("initial", falseFunction)) { c, t, e ->
                    if (c) t else e
                }
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
