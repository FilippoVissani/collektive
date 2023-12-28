package it.unibo.collektive.networking

import kotlinx.coroutines.flow.StateFlow

/**
 * TODO.
 *
 */
interface ReactiveNetwork {
    /**
     * TODO.
     *
     */
    fun write(message: OutboundMessage)

    /**
     * TODO.
     *
     */
    fun read(): StateFlow<Collection<InboundMessage>>
}
