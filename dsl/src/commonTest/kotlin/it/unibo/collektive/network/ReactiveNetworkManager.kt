package it.unibo.collektive.network

import it.unibo.collektive.ID
import it.unibo.collektive.flow.extensions.mapStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class ReactiveNetworkManager {
    private val messageBufferFlow: MutableStateFlow<Set<OutboundMessage>> = MutableStateFlow(emptySet())

    fun send(message: OutboundMessage) {
        messageBufferFlow.update { it + message }
    }

    fun receive(receiverId: ID): StateFlow<Collection<InboundMessage>> = mapStates(messageBufferFlow) { messageBuffer ->
        messageBuffer.filterNot { it.senderId == receiverId }
            .map { received ->
                InboundMessage(
                    received.senderId,
                    received.messages.mapValues { (_, single) ->
                        single.overrides.getOrElse(receiverId) { single.default }
                    }
                )
            }
    }
}
