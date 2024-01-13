package it.unibo.collektive.reactive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

suspend fun <R> runSimulation(simulation: Map<ReactiveAggregateResult<R>, MutableStateFlow<List<ReactiveInboundMessage>>>) = coroutineScope {
    println("###################################")
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
                                    val newMessage = ReactiveInboundMessage(
                                        aggregateResult.localId,
                                        mapOf(path to message.overrides.getOrElse(aggregateResult.localId) { message.default })
                                    )
                                    inboundMessages + newMessage
                                } else {
                                    val oldMessage =
                                        inboundMessages.first { it.senderId == aggregateResult.localId }
                                    val newMessage = ReactiveInboundMessage(
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
