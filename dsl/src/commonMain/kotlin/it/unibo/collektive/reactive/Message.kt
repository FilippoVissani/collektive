package it.unibo.collektive.reactive

import it.unibo.collektive.ID
import it.unibo.collektive.stack.Path
import kotlinx.coroutines.flow.StateFlow

/**
 * Types of messages.
 */
sealed interface Message

/**
 * [messages] received by a node from [senderId].
 */
data class InboundMessage(val senderId: ID, val messages: Map<Path, *>) : Message

/**
 * An [ReactiveOutboundMessage] are [messages] that a device [senderId] sends to all other neighbours.
 */
data class ReactiveOutboundMessage(
    val senderId: ID,
    val messages: Map<Path, StateFlow<SingleOutboundMessage<*>>>,
) : Message

/**
 * A [SingleOutboundMessage] contains the values associated to a [Path] in the [messages] of [ReactiveOutboundMessage].
 * Has a [default] value that is sent regardless the awareness the device's neighbours, [overrides] specifies the
 * payload depending on the neighbours values.
 */
data class SingleOutboundMessage<Payload>(val default: Payload, val overrides: Map<ID, Payload> = emptyMap())
