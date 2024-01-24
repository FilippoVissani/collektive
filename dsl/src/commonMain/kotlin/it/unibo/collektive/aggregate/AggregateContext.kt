package it.unibo.collektive.aggregate

import it.unibo.collektive.ID
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.ReactiveInboundMessage
import it.unibo.collektive.reactive.ReactiveOutboundMessage
import it.unibo.collektive.reactive.ReactiveSingleOutboundMessage
import it.unibo.collektive.reactive.ReactiveState
import it.unibo.collektive.reactive.flow.extensions.combineStates
import it.unibo.collektive.reactive.flow.extensions.mapStates
import it.unibo.collektive.reactive.getTyped
import it.unibo.collektive.stack.Path
import it.unibo.collektive.stack.Stack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Context for managing aggregate computation.
 * It represents the [localId] of the device, the [messages] received from the neighbours,
 * and the [previousState] of the device.
 */
class AggregateContext(
    private val localId: ID,
    private val reactiveInboundMessages: MutableStateFlow<List<ReactiveInboundMessage>>,
) {
    private val stack = Stack<Any>()
    private val state: ReactiveState = mutableMapOf()
    private var outboundMessages: ReactiveOutboundMessage = ReactiveOutboundMessage(localId, emptyMap())

    /**
     * Return the current state of the device as a new state.
     */
    fun state(): ReactiveState = state.toMap()

    /**
     * TODO.
     *
     */
    fun outboundMessages() = outboundMessages

    /**
     * TODO.
     *
     * @param reactiveInboundMessage
     */
    fun receiveMessage(reactiveInboundMessage: ReactiveInboundMessage) {
        reactiveInboundMessages.update { messages ->
            messages.filter { it.senderId != reactiveInboundMessage.senderId } + reactiveInboundMessage
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
    fun <T> exchange(initial: T, body: (StateFlow<Field<T>>) -> StateFlow<Field<T>>): StateFlow<Field<T>> {
        val messages = messagesAt<T>(stack.currentPath())
        val previous = stateAt(stack.currentPath(), initial)
        val subject = mapStates(messages) { m -> newField(previous.value, m) }
        return body(subject).also { flow ->
            val alignmentPath = stack.currentPath()
            val message = mapStates(flow) { field ->
                ReactiveSingleOutboundMessage(field.localValue, field.excludeSelf())
            }
            outboundMessages = outboundMessages.copy(messages = outboundMessages.messages + (alignmentPath to message))
            state.getTyped(alignmentPath, mapStates(flow) { it.localValue })
        }
    }

    /**
     * TODO.
     *
     * @param T
     * @param condition
     * @param th
     * @param el
     * @return
     */
    fun <T> mux(condition: () -> StateFlow<Boolean>, th: () -> StateFlow<T>, el: () -> StateFlow<T>): StateFlow<T> {
        return condition().let { conditionFlow ->
            val thFlow = th()
            val elFlow = el()
            combineStates(conditionFlow, thFlow, elFlow) { c, t, e ->
                if (c) t else e
            }
        }
    }

    /**
     * Alignment function that pushes in the stack the pivot, executes the body and pop the last
     * element of the stack after it is called.
     * Returns the body's return element.
     */
    fun <R> alignedOn(pivot: Any?, body: () -> R): R {
        stack.alignRaw(pivot)
        return body().also { stack.dealign() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> messagesAt(path: Path): StateFlow<Map<ID, T>> = mapStates(reactiveInboundMessages) { messages ->
        messages
            .filter { it.messages.containsKey(path) }
            .associate { it.senderId to it.messages[path] as T }
    }

    private fun <T> stateAt(path: Path, default: T): StateFlow<T> = state.getTyped(path, default)
}

/**
 * TODO.
 *
 * @param T
 * @param th
 * @param el
 * @return
 */
fun <T> StateFlow<Boolean>.branch(th: StateFlow<T>, el: StateFlow<T>): StateFlow<T> {
    return combineStates(this, th, el) { c, t, e ->
        if (c) t else e
    }
}
