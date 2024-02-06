package it.unibo.collektive.reactive

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.networking.InboundMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * TODO.
 *
 * @param ID
 * @param R
 * @property localId
 */
class RCollektive<ID : Any, R>(
    val localId: ID,
) {

    companion object {

        /**
         * TODO.
         *
         * @param ID
         * @param R
         * @param localId
         * @param rInboundMessages
         * @param compute
         * @return
         */
        fun <ID : Any, R> aggregate(
            localId: ID,
            rInboundMessages: MutableStateFlow<List<InboundMessage<ID>>> = MutableStateFlow(emptyList()),
            compute: Aggregate<ID>.() -> StateFlow<R>,
        ): RAggregateResult<ID, R> = RAggregateContext(localId, rInboundMessages).run {
            RAggregateResult(localId, compute(), rOutboundMessages(), rState())
        }
    }
}
