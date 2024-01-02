package it.unibo.collektive.reactive

import it.unibo.collektive.ID
import it.unibo.collektive.reactive.AggregateExpression
import it.unibo.collektive.reactive.aggregate.AggregateContext

/**
 * Create a Collektive device with a specific [id] and a [network] to manage incoming and outgoing messages,
 * the [computeFunction] is the function to apply within the [AggregateContext].
 */
class Collektive(
    val id: ID,
) {

    companion object {

        /**
         * Aggregate program entry point which computes an iteration of a device [localId], taking as parameters
         * the previous [state],
         * the [messages] received from the neighbours and the [aggregateExpression] with AggregateContext
         * object receiver that provides the aggregate constructs.
         */
        fun <R> aggregate(
            localId: ID,
            aggregateExpression: AggregateContext.() -> AggregateExpression<R>,
        ): AggregateExpression<R> = AggregateContext(localId).run {
            aggregateExpression()
        }
    }
}
