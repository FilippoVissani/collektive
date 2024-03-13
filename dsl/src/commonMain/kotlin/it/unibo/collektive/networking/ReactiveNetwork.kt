package it.unibo.collektive.networking

import kotlinx.coroutines.flow.StateFlow

/**
 * TODO.
 *
 */
interface ReactiveNetwork<ID : Any> {
    /**
     * TODO.
     *
     */
    fun write(message: OutboundMessage<ID>)

    /**
     * TODO.
     *
     */
    fun read(): StateFlow<Collection<InboundMessage<ID>>>
}
