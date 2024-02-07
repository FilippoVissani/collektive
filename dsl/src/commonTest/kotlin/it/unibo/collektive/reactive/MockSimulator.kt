package it.unibo.collektive.reactive

import it.unibo.collektive.networking.InboundMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

suspend fun <ID : Any, R> printResults(aggregateResult: RAggregateResult<ID, R>) = coroutineScope {
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
            println("${aggregateResult.localId} -> toSend: $it")
        }
    }
}

suspend fun <ID : Any, R> runSimulation(
    simulation: Map<
        RAggregateResult<ID, R>,
        MutableStateFlow<List<InboundMessage<ID>>>,
        >,
) =
    coroutineScope {
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
                                neighborInboundMessages.filter {
                                    it.senderId != localDevice.localId
                                } + outboundMessage.messages.map { (path, singleOutboundMessage) ->
                                    InboundMessage(
                                        localDevice.localId,
                                        mapOf(
                                            path to singleOutboundMessage
                                                .overrides
                                                .getOrElse(localDevice.localId) { singleOutboundMessage.default },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
