package it.unibo.collektive

import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.reactive.ReactiveAggregateResult
import it.unibo.collektive.reactive.ReactiveInboundMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Create a Collektive device with a specific [id] and a [network] to manage incoming and outgoing messages,
 * the [computeFunction] is the function to apply within the [AggregateContext].
 */
object Collektive {
    /**
     * TODO.
     *
     * @param localId
     * @param compute
     */
    fun <R> aggregate(
        localId: ID,
        reactiveInboundMessages: MutableStateFlow<List<ReactiveInboundMessage>> = MutableStateFlow(emptyList()),
        compute: AggregateContext.() -> StateFlow<R>,
    ): ReactiveAggregateResult<R> = AggregateContext(localId, reactiveInboundMessages).run {
        ReactiveAggregateResult(localId, compute(), outboundMessages(), state())
    }
}
