package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import it.unibo.collektive.Collektive.aggregate
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    suspend fun <R> printResults(aggregateResult: ReactiveAggregateResult<R>) = coroutineScope {
        launch(Dispatchers.Default) {
            aggregateResult.result.collect {
                println("${aggregateResult.localId} -> result: $it")
            }
        }
        aggregateResult.state.forEach { (path, flow) ->
            launch(Dispatchers.Default) {
                flow.collect {
                    println("${aggregateResult.localId} -> state: $path -> $it")
                }
            }
        }
        aggregateResult.toSend.messages.forEach { (path, flow) ->
            launch(Dispatchers.Default) {
                flow.collect {
                    println("${aggregateResult.localId} -> message: $path -> $it")
                }
            }
        }
    }

    suspend fun <R> runSimulation(simulation: Map<ReactiveAggregateResult<R>, MutableStateFlow<List<InboundMessage>>>) = coroutineScope {
        simulation.keys.forEach {
            launch(Dispatchers.Default) {
                printResults(it)
            }
        }
        simulation.forEach { (aggregateResult, _) ->
            aggregateResult.toSend.messages.forEach { (path, flow) ->
                launch(Dispatchers.Default) {
                    flow.collect { message ->
                        // Update neighbor's channel when the device generate new message
                        simulation
                            .filter { (neighbor, _) -> neighbor.localId != aggregateResult.localId }
                            .forEach { (_, channel) ->
                                channel.update { inboundMessages ->
                                    // if this device already sent a message, that message is updated. New message is generated otherwise.
                                    if (inboundMessages.none { it.senderId == aggregateResult.localId }) {
                                        val newMessage = InboundMessage(
                                            aggregateResult.localId,
                                            mapOf(path to message.overrides.getOrElse(aggregateResult.localId) { message.default })
                                        )
                                        inboundMessages + newMessage
                                    } else {
                                        val oldMessage =
                                            inboundMessages.first { it.senderId == aggregateResult.localId }
                                        val newMessage = InboundMessage(
                                            oldMessage.senderId,
                                            oldMessage.messages + (path to message.overrides.getOrElse(aggregateResult.localId) { message.default }),
                                        )
                                        inboundMessages.filter { it.senderId != aggregateResult.localId } + newMessage
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            val aggregateResult0 = aggregate(id0) {
                exchange(initV1, increaseOrDouble)
            }
            println("#############################")
            val job = launch(Dispatchers.Default) {
                runSimulation(mapOf(aggregateResult0 to MutableStateFlow(emptyList())))
            }
            delay(100)
            job.cancelAndJoin()
        }
    }

    "Exchange should work between three aligned devices" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val channel2: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())

            val aggregateResult0 = aggregate(id0, channel0) {
                exchange(initV1, increaseOrDouble)
            }

            val aggregateResult1 = aggregate(id1, channel1) {
                exchange(initV2, increaseOrDouble)
            }

            val aggregateResult2 = aggregate(id2, channel2) {
                exchange(initV3, increaseOrDouble)
            }
            println("#############################")
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
        }
    }

    "Devices should be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())

            val aggregateResult0 = aggregate(id0, channel0) {
                if(true) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
            }

            val aggregateResult1 = aggregate(id1, channel1) {
                if(true) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
            }
            println("#############################")
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

    "Devices should not be aligned" {
        runBlocking {
            val channel0: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
            val channel1: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())

            val aggregateResult0 = aggregate(id0, channel0) {
                if(true) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
            }

            val aggregateResult1 = aggregate(id1, channel1) {
                if(false) exchange(true, alwaysTrue) else exchange(false, alwaysFalse)
            }
            println("#############################")
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
