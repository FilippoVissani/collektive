package it.unibo.collektive.reactive

import it.unibo.collektive.proactive.networking.InboundMessage
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

suspend fun <R> runSimulation(simulation: Map<ReactiveAggregateResult<R>, MutableStateFlow<List<InboundMessage>>>) = coroutineScope {
    println("###################################")
    simulation.keys.forEach {
        launch(Dispatchers.Default) {
            printResults(it)
        }
    }
    simulation.forEach { (localDevice, _) ->
        simulation
            .filter { (neighbor, _) -> neighbor.localId != localDevice.localId }
            .forEach { (_, neighborChannel) ->
                launch(Dispatchers.Default) {
                    localDevice.toSend.collect { outboundMessage ->
                        neighborChannel.update { neighborInboundMessages ->
                            neighborInboundMessages.filter { it.senderId != localDevice.localId } + outboundMessage.messages.map { (path, singleOutboundMessage) ->
                                InboundMessage(
                                    localDevice.localId,
                                    mapOf(path to singleOutboundMessage.overrides.getOrElse(localDevice.localId) { singleOutboundMessage.default })
                                )
                            }
                        }
                    }
                }
            }
    }
}
