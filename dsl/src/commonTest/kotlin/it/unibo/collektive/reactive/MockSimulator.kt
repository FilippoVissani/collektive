package it.unibo.collektive.reactive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

suspend fun <R> printResults(aggregateResult: ReactiveAggregateResult<R>) = coroutineScope {
    launch(Dispatchers.Default) {
        aggregateResult.result.collect {
            println("${aggregateResult.localId} -> result: $it")
        }
    }

    launch(Dispatchers.Default) {
        aggregateResult.state.collect { state ->
            println("${aggregateResult.localId} -> state: $state")
        }
    }

    launch(Dispatchers.Default) {
        aggregateResult.toSend.collect {
            println("${aggregateResult.localId} -> message: $it")
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
        launch(Dispatchers.Default) {
            aggregateResult.toSend.collect { outboundMessage ->
                // Update neighbor's channel when the device generate new message
                simulation
                    .filter { (neighbor, _) -> neighbor.localId != aggregateResult.localId }
                    .forEach { (_, channel) ->
                        channel.update { inboundMessages ->
                            inboundMessages.filter {
                                it.senderId != aggregateResult.localId
                            } + outboundMessage.messages.map { (path, singleOutboundMessage) ->
                                ReactiveInboundMessage(
                                    aggregateResult.localId,
                                    mapOf(path to singleOutboundMessage.overrides.getOrElse(aggregateResult.localId) { singleOutboundMessage.default })
                                )
                            }
                        }
                    }
            }
        }
    }
}
