package it.unibo.collektive

import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.proactive.networking.OutboundMessage
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
     * @param aggregateExpression
     */
    fun aggregate(
        localId: ID,
        aggregateExpression:
        AggregateContext.() -> StateFlow<OutboundMessage>,
    ): StateFlow<OutboundMessage> = AggregateContext(localId).run {
        aggregateExpression()
    }
}
