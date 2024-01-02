package it.unibo.collektive.reactive.aggregate

import it.unibo.collektive.ID
import it.unibo.collektive.field.Field
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import it.unibo.collektive.reactive.AggregateExpression
import it.unibo.collektive.reactive.flow.extensions.combineStates
import it.unibo.collektive.reactive.flow.extensions.mapStates
import it.unibo.collektive.stack.Path
import it.unibo.collektive.stack.Stack
import it.unibo.collektive.state.State
import it.unibo.collektive.state.getTyped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Context for managing aggregate computation.
 * It represents the [localId] of the device, the [messages] received from the neighbours,
 * and the [previousState] of the device.
 */
class AggregateContext(
    private val localId: ID,
) {
    private val stack = Stack<Any>()
    private val states: MutableStateFlow<State> = MutableStateFlow(emptyMap())
    private val inboundMessages: MutableStateFlow<Iterable<InboundMessage>> = MutableStateFlow(emptyList())

    /**
     * Return the current state of the device as a new state.
     */
    fun state(): StateFlow<State> = states.asStateFlow()

    /**
     * TODO.
     *
     * @param inboundMessage
     */
    fun receiveMessage(inboundMessage: InboundMessage) {
        inboundMessages.update { messages ->
            messages.filter { it.senderId != inboundMessage.senderId } + inboundMessage
        }
    }

    private fun <T> newField(localValue: T, others: Map<ID, T>): Field<T> = Field(localId, localValue, others)

    /**
     * This function computes the local value of e_i, substituting variable n with the nvalue w of
     * messages received from neighbours, using the local value of e_i ([initial]) as a default.
     * The exchange returns the neighbouring or local value v_r from the evaluation of e_r applied to the [body].
     * e_s evaluates to a nvalue w_s consisting of local values to be sent to neighbour devices δ′,
     * which will use their corresponding w_s(δ') as soon as they wake up and perform their next execution round.
     *
     * Often, expressions e_r and e_s coincide, so this function provides a shorthand for exchange(e_i, (n) => (e, e)).
     *
     * ## Example
     * ```
     * exchange(0){ f ->
     *  f.mapField { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
     * }
     * ```
     * The result of the exchange function is a field with as messages a map with key the id of devices across the
     * network and the result of the computation passed as relative local values.
     */
    fun <T> exchange(initial: T, body: (Field<T>) -> Field<T>): AggregateExpression<Field<T>> {
        val messages = messagesAt<T>(stack.currentPath())
        val previous = stateAt(stack.currentPath(), initial)
        val subject = mapStates(messages) { m -> newField(previous.value, m) }
        return AggregateExpression {
            mapStates(subject) { field ->
                body(field).let { result ->
                    val message = SingleOutboundMessage(result.localValue, result.excludeSelf())
                    states.update { it + (stack.currentPath() to result.localValue) }
                    OutboundMessage(localId, mapOf(stack.currentPath() to message))
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> messagesAt(path: Path): StateFlow<Map<ID, T>> = mapStates(inboundMessages) { messages ->
        messages
            .filter { it.messages.containsKey(path) }
            .associate { it.senderId to it.messages[path] as T }
    }

    private fun <T> stateAt(path: Path, default: T): StateFlow<T> = mapStates(states) { it.getTyped(path, default) }
}
