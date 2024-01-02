package it.unibo.collektive.reactive

import it.unibo.collektive.networking.OutboundMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * TODO.
 *
 * @param R
 */
interface AggregateExpression<T> {
    /**
     * TODO.
     *
     * @param path
     * @return
     */
    fun compute(): StateFlow<OutboundMessage>

    companion object {
        /**
         * TODO.
         *
         * @param T
         * @param f
         * @return
         */
        operator fun <T> invoke(f: () -> StateFlow<OutboundMessage>): AggregateExpression<T> {
            return AggregateExpressionImpl(f)
        }
    }
}

internal class AggregateExpressionImpl<T>(private val f: () -> StateFlow<OutboundMessage>) :
    AggregateExpression<T> {

    override fun compute(): StateFlow<OutboundMessage> = f()
}
