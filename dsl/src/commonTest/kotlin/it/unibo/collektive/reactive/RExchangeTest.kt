package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import it.unibo.collektive.field.Field
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RExchangeTest : StringSpec({

    // device ids
    val id0 = 0
    val id1 = 1
    val id2 = 2

    // initial values
    val initV1 = 1
    val trueCondition = true
    val falseCondition = false

    // expected
    val expected2 = 2
    val expectedTrue = true
    val expectedFalse = false

    val increaseOrDouble: (StateFlow<Field<Int, Int>>) -> StateFlow<Field<Int, Int>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
        }
    }

    val alwaysTrue: (StateFlow<Field<Int, Boolean>>) -> StateFlow<Field<Int, Boolean>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> true }
        }
    }

    val alwaysFalse: (StateFlow<Field<Int, Boolean>>) -> StateFlow<Field<Int, Boolean>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> false }
        }
    }

    "First time rExchange should return the initial value" {
        runBlocking {
            val aggregateResult0 = RCollektive.aggregate(id0) {
                val res = rExchange(initV1, increaseOrDouble)
                res.value.localValue shouldBe expected2
                res
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(mapOf(aggregateResult0 to MutableStateFlow(emptyList())))
            }
            delay(100)
            job.cancelAndJoin()
            aggregateResult0.toSend.value.senderId shouldBe id0
            aggregateResult0.toSend.value.messages.values shouldContain SingleOutboundMessage(expected2, emptyMap())
        }
    }

    "rExchange should work between three aligned devices" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage<Int>>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage<Int>>> = MutableStateFlow(emptyList())
            val channel2: MutableStateFlow<List<InboundMessage<Int>>> = MutableStateFlow(emptyList())

            val aggregateResult0 = RCollektive.aggregate(id0, channel0) {
                rExchange(trueCondition, alwaysTrue)
            }

            val aggregateResult1 = RCollektive.aggregate(id1, channel1) {
                rExchange(trueCondition, alwaysTrue)
            }

            val aggregateResult2 = RCollektive.aggregate(id2, channel2) {
                rExchange(trueCondition, alwaysTrue)
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(
                    mapOf(
                        aggregateResult0 to channel0,
                        aggregateResult1 to channel1,
                        aggregateResult2 to channel2,
                    ),
                )
            }
            delay(100)
            job.cancelAndJoin()
            aggregateResult0
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult0
                .toSend
                .value
                .senderId shouldBe id0
            aggregateResult0
                .toSend
                .value
                .messages.values shouldContain SingleOutboundMessage(
                expectedTrue,
                mapOf(
                    (id2 to expectedTrue),
                    (id1 to expectedTrue),
                ),
            )

            aggregateResult1
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult1
                .toSend
                .value
                .senderId shouldBe id1
            aggregateResult1
                .toSend
                .value
                .messages.values shouldContain SingleOutboundMessage(
                expectedTrue,
                mapOf((id0 to expectedTrue), (id2 to expectedTrue)),
            )

            aggregateResult2
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult2
                .toSend
                .value
                .senderId shouldBe id2
            aggregateResult2
                .toSend
                .value
                .messages.values shouldContain SingleOutboundMessage(
                expectedTrue,
                mapOf(
                    (id1 to expectedTrue),
                    (id0 to expectedTrue),
                ),
            )
        }
    }

    "Devices should be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage<Int>>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage<Int>>> = MutableStateFlow(emptyList())

            val aggregateResult0 = RCollektive.aggregate(id0, channel0) {
                if (trueCondition) rExchange(true, alwaysTrue) else rExchange(false, alwaysFalse)
            }

            val aggregateResult1 = RCollektive.aggregate(id1, channel1) {
                if (trueCondition) rExchange(true, alwaysTrue) else rExchange(false, alwaysFalse)
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(
                    mapOf(
                        aggregateResult0 to channel0,
                        aggregateResult1 to channel1,
                    ),
                )
            }
            delay(100)
            job.cancelAndJoin()
            aggregateResult0
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult0
                .toSend
                .value
                .senderId shouldBe id0
            aggregateResult0
                .toSend
                .value
                .messages.values shouldContain SingleOutboundMessage(
                expectedTrue,
                mapOf(
                    (id1 to expectedTrue),
                ),
            )

            aggregateResult1
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult1
                .toSend
                .value
                .senderId shouldBe id1
            aggregateResult1
                .toSend
                .value
                .messages.values shouldContain SingleOutboundMessage(
                expectedTrue,
                mapOf(
                    (id0 to expectedTrue),
                ),
            )
        }
    }

    "Devices should not be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage<Int>>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage<Int>>> = MutableStateFlow(emptyList())

            val aggregateResult0 = RCollektive.aggregate(id0, channel0) {
                if (trueCondition) rExchange(true, alwaysTrue) else rExchange(false, alwaysFalse)
            }

            val aggregateResult1 = RCollektive.aggregate(id1, channel1) {
                if (falseCondition) rExchange(true, alwaysTrue) else rExchange(false, alwaysFalse)
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(
                    mapOf(
                        aggregateResult0 to channel0,
                        aggregateResult1 to channel1,
                    ),
                )
            }
            delay(100)
            job.cancelAndJoin()
            aggregateResult0
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult0
                .toSend
                .value
                .senderId shouldBe id0
            aggregateResult0
                .toSend
                .value
                .messages.values shouldContain SingleOutboundMessage(
                expectedTrue,
                emptyMap(),
            )

            aggregateResult1
                .result
                .value
                .localValue shouldBe expectedFalse
            aggregateResult1
                .toSend
                .value
                .senderId shouldBe id1
            aggregateResult1
                .toSend
                .value
                .messages.values shouldContain SingleOutboundMessage(
                expectedFalse,
                emptyMap(),
            )
        }
    }
})
