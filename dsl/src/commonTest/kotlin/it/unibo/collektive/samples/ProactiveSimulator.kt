package it.unibo.collektive.samples

import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.networking.InboundMessage
import kotlin.test.Test

class ProactiveSimulator {

    @Test
    fun runSimulation() {
        val contexts = (0..<environment.devicesNumber)
        var results = contexts.map { deviceId ->
            aggregate(deviceId) { gradientWithObstacles(getNodeType(deviceId)) }
        }
        (0..<100).map {
            results = results.map { aggregateResult ->
                val neighborMessages = results
                    .filter { environment.neighbors(aggregateResult.localId).contains(it.localId) }
                    .map { neighborResult ->
                        InboundMessage(
                            neighborResult.localId,
                            neighborResult.toSend.messagesFor(aggregateResult.localId),
                        )
                    }
                aggregate(aggregateResult.localId, aggregateResult.newState, neighborMessages) {
                    gradientWithObstacles(getNodeType(aggregateResult.localId))
                }
            }
            results.forEach {
                println("${it.localId} -> ${it.result}")
            }
        }
    }
}
