package it.unibo.collektive.reactive

import it.unibo.collektive.flow.extensions.mapStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class MockReactiveNetworkManager {
    private val messageBufferFlow: MutableStateFlow<Set<OutboundMessage<Int>>> = MutableStateFlow(emptySet())

    fun send(message: OutboundMessage<Int>) {
        messageBufferFlow.update { it + message }
    }

    fun receive(receiverId: Int): StateFlow<Collection<InboundMessage<Int>>> = messageBufferFlow.mapStates {
            messageBuffer ->
        messageBuffer.filterNot { it.senderId == receiverId }
            .map { received ->
                InboundMessage(
                    received.senderId,
                    received.messages.mapValues { (_, single) ->
                        single.overrides.getOrElse(receiverId) { single.default }
                    },
                )
            }
    }
}
