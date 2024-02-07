package it.unibo.collektive.reactive

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.YieldingScope
import it.unibo.collektive.aggregate.api.impl.stack.Stack
import it.unibo.collektive.field.Field
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import it.unibo.collektive.path.Path
import it.unibo.collektive.reactive.flow.extensions.flattenConcat
import it.unibo.collektive.reactive.flow.extensions.mapStates
import it.unibo.collektive.state.State
import it.unibo.collektive.state.impl.getTyped
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * TODO.
 *
 * @param ID
 * @property localId
 * @property rInboundMessages
 */
class RAggregateContext<ID : Any>(
    override val localId: ID,
    private val rInboundMessages: MutableStateFlow<List<InboundMessage<ID>>> = MutableStateFlow(emptyList()),
) : Aggregate<ID> {

    private val stack = Stack()
    private val rState: MutableStateFlow<State> = MutableStateFlow(emptyMap())
    private val rOutboundMessages: MutableStateFlow<OutboundMessage<ID>> =
        MutableStateFlow(OutboundMessage(localId, emptyMap()))

    /**
     * TODO.
     *
     * @return
     */
    fun rState(): StateFlow<State> = rState.asStateFlow()

    /**
     * TODO.
     *
     */
    fun rOutboundMessages() = rOutboundMessages.asStateFlow()

    @OptIn(DelicateCoroutinesApi::class)
    override fun <T> rExchange(
        initial: T,
        body: (StateFlow<Field<ID, T>>) -> StateFlow<Field<ID, T>>,
    ): StateFlow<Field<ID, T>> {
        val messages = rMessagesAt<T>(stack.currentPath())
        val previous = rStateAt(stack.currentPath(), initial)
        val subject = mapStates(messages) { m -> newField(previous.value, m) }
        val alignmentPath = stack.currentPath()
        return body(subject).also { flow ->
            flow.onEach { field ->
                val message = SingleOutboundMessage(field.localValue, field.excludeSelf())
                rOutboundMessages.update { it.copy(messages = it.messages + (alignmentPath to message)) }
                rState.update { it + (alignmentPath to field.localValue) }
            }.launchIn(GlobalScope)
        }
    }

    override fun <T> rBranch(
        condition: () -> StateFlow<Boolean>,
        th: () -> StateFlow<T>,
        el: () -> StateFlow<T>,
    ): StateFlow<T> {
        val currentPath = stack.currentPath()
        val conditionResult = condition()
        return flattenConcat(
            mapStates(conditionResult) { newCondition ->
                currentPath.tokens().forEach { stack.alignRaw(it) }
                if (newCondition) {
                    // Deletes false branch from messages and state
                    deleteOppositeBranch(newCondition)
                    alignedOn(newCondition) { th() }
                } else {
                    // Deletes true branch from messages and state
                    deleteOppositeBranch(newCondition)
                    alignedOn(newCondition) { el() }
                }.also {
                    currentPath.tokens().forEach { _ -> stack.dealign() }
                }
            },
        )
    }

    private fun deleteOppositeBranch(condition: Boolean) {
        alignedOn(!condition) {
            val oppositePath = stack.currentPath()
            rOutboundMessages.update {
                it.copy(messages = it.messages.filterNot { (p, _) -> isSublist(p.tokens(), oppositePath.tokens()) })
            }
            rState.update { it.filterNot { (p, _) -> isSublist(p.tokens(), oppositePath.tokens()) } }
        }
    }

    private fun <T> isSublist(listX: List<T>, listY: List<T>): Boolean {
        // Iterate over each element in list A
        for (i in 0..listX.size - listY.size) {
            var match = true
            // Check if sublist starting from index i matches list B
            for (j in listY.indices) {
                if (listX[i + j] != listY[j]) {
                    match = false
                    break
                }
            }
            // If all elements in B match sublist in A, return true
            if (match) return true
        }
        // If no sublist in A matches B, return false
        return false
    }

    private fun <T> newField(localValue: T, others: Map<ID, T>): Field<ID, T> = Field(localId, localValue, others)

    @Suppress("UNCHECKED_CAST")
    private fun <T> rMessagesAt(path: Path): StateFlow<Map<ID, T>> = mapStates(rInboundMessages) { messages ->
        messages
            .filter { it.messages.containsKey(path) }
            .associate { it.senderId to it.messages[path] as T }
    }

    private fun <T> rStateAt(path: Path, default: T): StateFlow<T> = mapStates(rState) { state ->
        state.getTyped(path, default)
    }

    override fun <Initial> exchange(
        initial: Initial,
        body: (Field<ID, Initial>) -> Field<ID, Initial>,
    ): Field<ID, Initial> {
        TODO("Not yet implemented")
    }

    override fun <Initial, Return> exchanging(
        initial: Initial,
        body: YieldingScope<Field<ID, Initial>, Field<ID, Return>>,
    ): Field<ID, Return> {
        TODO("Not yet implemented")
    }

    override fun <Initial> repeat(initial: Initial, transform: (Initial) -> Initial): Initial {
        TODO("Not yet implemented")
    }

    override fun <Initial, Return> repeating(initial: Initial, transform: YieldingScope<Initial, Return>): Return {
        TODO("Not yet implemented")
    }

    override fun <R> alignedOn(pivot: Any?, body: () -> R): R {
        stack.alignRaw(pivot)
        return body().also { stack.dealign() }
    }
}
