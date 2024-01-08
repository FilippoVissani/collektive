package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.Collektive.aggregate
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.flow.extensions.mapStates
import it.unibo.collektive.stack.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExchangeTest : StringSpec({

    // device ids
    val id0 = IntId(0)
    val id1 = IntId(1)
    val id2 = IntId(2)

    // initial values
    val initV1 = 1
    val initV2 = 2
    val initV3 = 3
    val trueCondition = true
    val falseCondition = false

    // paths
    val path1 = Path(listOf("invoke.1", "exchange.1"))
    val truePath = Path(listOf("invoke.1", "true", "exchange.1"))
    val falsePath = Path(listOf("invoke.1", "false", "exchange.1"))

    // expected
    val expected2 = 2
    val expected3 = 3
    val expected6 = 6
    val expected7 = 7
    val expectedTrue = true
    val expectedFalse = false

    val increaseOrDouble: (StateFlow<Field<Int>>) -> StateFlow<Field<Int>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
        }
    }

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

    "First time exchange should return the initial value" {
        runBlocking {
            val aggregateResult0 = aggregate(id0) {
                exchange(initV1, increaseOrDouble)
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(mapOf(aggregateResult0 to MutableStateFlow(emptyList())))
            }
            delay(100)
            job.cancelAndJoin()
            aggregateResult0
                .result
                .value
                .localValue shouldBe expected2
            aggregateResult0
                .toSend
                .senderId shouldBe id0
            aggregateResult0
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(path1 to ReactiveSingleOutboundMessage(expected2, emptyMap()))
        }
    }

    "Exchange should work between three aligned devices" {
        runBlocking {
            val channel0: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val channel2: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())

            val aggregateResult0 = aggregate(id0, channel0) {
                exchange(initV1, increaseOrDouble)
            }

            val aggregateResult1 = aggregate(id1, channel1) {
                exchange(initV2, increaseOrDouble)
            }

            val aggregateResult2 = aggregate(id2, channel2) {
                exchange(initV3, increaseOrDouble)
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(
                    mapOf(
                        aggregateResult0 to channel0,
                        aggregateResult1 to channel1,
                        aggregateResult2 to channel2,
                    )
                )
            }
            delay(100)
            job.cancelAndJoin()
            aggregateResult0
                .result
                .value
                .localValue shouldBe expected2
            aggregateResult0
                .toSend
                .senderId shouldBe id0
            aggregateResult0
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(path1 to ReactiveSingleOutboundMessage(expected2, mapOf((id2 to expected7), (id1 to expected6))))

            aggregateResult1
                .result
                .value
                .localValue shouldBe expected3
            aggregateResult1
                .toSend
                .senderId shouldBe id1
            aggregateResult1
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(path1 to ReactiveSingleOutboundMessage(expected3, mapOf((id0 to expected3), (id2 to expected7))))

            aggregateResult2
                .result
                .value
                .localValue shouldBe expected6
            aggregateResult2
                .toSend
                .senderId shouldBe id2
            aggregateResult2
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(path1 to ReactiveSingleOutboundMessage(expected6, mapOf((id1 to expected6), (id0 to expected3))))
        }
    }

    "Devices should be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())

            val aggregateResult0 = aggregate(id0, channel0) {
                if (trueCondition) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
            }

            val aggregateResult1 = aggregate(id1, channel1) {
                if (trueCondition) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
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
            aggregateResult0
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult0
                .toSend
                .senderId shouldBe id0
            aggregateResult0
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(truePath to ReactiveSingleOutboundMessage(expectedTrue, mapOf((id1 to expectedTrue))))

            aggregateResult1
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult1
                .toSend
                .senderId shouldBe id1
            aggregateResult1
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(truePath to ReactiveSingleOutboundMessage(expectedTrue, mapOf((id0 to expectedTrue))))
        }
    }

    "Devices should not be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList())

            val aggregateResult0 = aggregate(id0, channel0) {
                if (trueCondition) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
            }

            val aggregateResult1 = aggregate(id1, channel1) {
                if (falseCondition) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
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
            aggregateResult0
                .result
                .value
                .localValue shouldBe expectedTrue
            aggregateResult0
                .toSend
                .senderId shouldBe id0
            aggregateResult0
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(truePath to ReactiveSingleOutboundMessage(expectedTrue, emptyMap()))

            aggregateResult1
                .result
                .value
                .localValue shouldBe expectedFalse
            aggregateResult1
                .toSend
                .senderId shouldBe id1
            aggregateResult1
                .toSend
                .messages
                .map { (k, v) -> k to v.value } shouldBe mapOf(falsePath to ReactiveSingleOutboundMessage(expectedFalse, emptyMap()))
        }
    }
})
