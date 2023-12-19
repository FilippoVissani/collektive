package it.unibo.collektive.aggregate

import it.unibo.collektive.ID
import it.unibo.collektive.aggregate.ops.RepeatingContext
import it.unibo.collektive.aggregate.ops.RepeatingContext.RepeatingResult
import it.unibo.collektive.field.Field
import it.unibo.collektive.flow.extensions.combineStates
import it.unibo.collektive.flow.extensions.mapStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import it.unibo.collektive.stack.Path
import it.unibo.collektive.stack.Stack
import it.unibo.collektive.state.State
import it.unibo.collektive.state.getTyped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Context for managing aggregate computation.
 * It represents the [localId] of the device, the [messages] received from the neighbours,
 * and the [previousState] of the device.
 */
class AggregateContext(private val localId: ID) {

    private val stack: Stack<Any> = Stack()
    private val _states: MutableStateFlow<State> = MutableStateFlow(emptyMap())
    private val _outboundMessages: MutableStateFlow<OutboundMessage> =
        MutableStateFlow(OutboundMessage(localId, emptyMap()))
    private val _inboundMessages: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
    val outboundMessages = _outboundMessages.asStateFlow()
    val states = _states.asStateFlow()

    fun receiveMessage(message: InboundMessage) {
        _inboundMessages.update { messages -> messages.filter { it.senderId != message.senderId } + message }
    }

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
    suspend fun <T> exchange(initial: T, body: (StateFlow<Field<T>>) -> StateFlow<Field<T>>): StateFlow<Field<T>> = coroutineScope {
        val currentPath = stack.currentPath()
        val inMessages: StateFlow<Map<ID, T>> = messagesAt(currentPath)
        val previous: StateFlow<T> = stateAt(currentPath, initial)
        val subject: StateFlow<Field<T>> = combineStates(previous, inMessages) { p, m -> newField(p, m) }
        val result = body(subject)

        launch(Dispatchers.Default) {
            result.collect { field ->
                val outMessage = SingleOutboundMessage(field.localValue, field.excludeSelf())
                check(!outboundMessages.value.messages.containsKey(currentPath)) {
                    "Alignment was broken by multiple aligned calls with the same path: $currentPath. " +
                        "The most likely cause is an aggregate function call within a loop"
                }
                _outboundMessages.update {
                    it.copy(messages = outboundMessages.value.messages + (currentPath to outMessage))
                }
                _states.update { it + (stack.currentPath() to field.localValue) }
            }
        }

        result
    }

    /**
     * Iteratively updates the value computing the [transform] expression from a [RepeatingContext]
     * at each device using the last computed value or the [initial].
     */
    suspend fun <T, R> repeating(
        initial: T,
        transform: RepeatingContext<T, R>.(StateFlow<T>) -> StateFlow<RepeatingResult<T, R>>,
    ): StateFlow<R> = coroutineScope {
        val currentPath = stack.currentPath()
        val context = RepeatingContext<T, R>()
        val result: StateFlow<RepeatingResult<T, R>> = transform(context, stateAt(currentPath, initial))

        launch(Dispatchers.Default) {
            result.collect { repeatingResult ->
                _states.update { it + (currentPath to repeatingResult.toReturn) }
            }
        }

        mapStates(result) { it.toReturn }
    }

    /**
     * Iteratively updates the value computing the [transform] expression at each device using the last
     * computed value or the [initial].
     */
    suspend fun <T> repeat(
        initial: T,
        transform: (StateFlow<T>) -> StateFlow<T>,
    ): StateFlow<T> =
        repeating(initial) { repeatingContext ->
            val result = transform(repeatingContext)
            mapStates(result) {
                RepeatingResult(it, it)
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

    private fun <T> newField(localValue: T, others: Map<ID, T>): Field<T> = Field(localId, localValue, others)

    @Suppress("UNCHECKED_CAST")
    private fun <T> messagesAt(path: Path): StateFlow<Map<ID, T>> {
        return mapStates(_inboundMessages) { message ->
            message.filter { it.messages.containsKey(path) }
                .associate { it.senderId to it.messages[path] as T }
        }
    }

    private fun <T> stateAt(path: Path, default: T): StateFlow<T> = mapStates(_states) { state ->
        state.getTyped(path, default)
    }
}
