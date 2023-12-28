package it.unibo.collektive.network

import it.unibo.collektive.ID
import it.unibo.collektive.flow.extensions.mapStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class ReactiveNetworkManager {
    private val messageBufferFlow: MutableStateFlow<Set<OutboundMessage>> = MutableStateFlow(emptySet())

    fun send(message: OutboundMessage) {
        messageBufferFlow.update { it + message }
    }

    fun receive(receiverId: ID): StateFlow<Collection<InboundMessage>> {
        return mapStates(messageBufferFlow) { messageBuffer ->
            messageBuffer.filterNot { it.senderId == receiverId }
                .map { received ->
                    InboundMessage(
                        received.senderId,
                        received.messages.mapValues { (_, single) ->
                            single.overrides.getOrElse(receiverId) { single.default }
                        }
                    )
                }.also {
                    remove(receiverId)
                }
        }
    }

    private fun remove(receiverId: ID) {
        messageBufferFlow.update { messageBuffer ->
            messageBuffer.mapNotNull { outbound ->
                if (outbound.senderId != receiverId) {
                    val newOutbound = OutboundMessage(
                        outbound.senderId,
                        outbound.messages.mapValues { (_, message) ->
                            SingleOutboundMessage(
                                message.default,
                                message.overrides.filterNot { it.key == receiverId },
                            )
                        },
                    )
                    if (newOutbound.messages.filterNot { it.value.overrides.isEmpty() }.isNotEmpty()) {
                        newOutbound
                    } else {
                        null
                    }
                } else {
                    outbound
                }
            }.toSet()
        }
    }
}