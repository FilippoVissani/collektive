package it.unibo.collektive.reactive

import it.unibo.collektive.ID
import it.unibo.collektive.proactive.networking.OutboundMessage
import it.unibo.collektive.state.State
import kotlinx.coroutines.flow.StateFlow

/**
 * Result of the aggregate computation.
 * It represents the [localId] of the device, the [result] of the computation,
 * the messages [toSend] to other devices and the [state] of the device.
 */
data class ReactiveAggregateResult<R>(
    val localId: ID,
    val result: StateFlow<R>,
    val toSend: StateFlow<OutboundMessage>,
    val state: StateFlow<State>,
)
