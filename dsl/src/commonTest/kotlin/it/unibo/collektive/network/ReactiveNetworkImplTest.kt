package it.unibo.collektive.network

import it.unibo.collektive.ID
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.ReactiveNetwork
import kotlinx.coroutines.flow.StateFlow

class ReactiveNetworkImplTest(private val networkManager: ReactiveNetworkManager, private val localId: ID) :
    ReactiveNetwork {
    override fun write(message: OutboundMessage) {
        networkManager.send(message)
    }

    override fun read(): StateFlow<Collection<InboundMessage>> {
        return networkManager.receive(localId)
    }
}
