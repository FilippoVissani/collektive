package it.unibo.collektive.reactive

import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.ReactiveNetwork
import kotlinx.coroutines.flow.StateFlow

class MockReactiveNetwork(private val networkManager: MockReactiveNetworkManager, private val localId: Int) :
    ReactiveNetwork<Int> {
    override fun write(message: OutboundMessage<Int>) {
        networkManager.send(message)
    }

    override fun read(): StateFlow<Collection<InboundMessage<Int>>> {
        return networkManager.receive(localId)
    }
}
