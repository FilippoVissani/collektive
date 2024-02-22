package it.unibo.collektive.samples

import io.kotest.common.runBlocking
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.reactive.RCollektive.Companion.aggregate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.test.Test

class Simulator {

    data class DeviceContext(
        val id: Int,
        val inboundMessages: MutableStateFlow<Iterable<InboundMessage<Int>>>,
    )

    private suspend fun gradientSimulation() = coroutineScope {
        val contexts = (0..<environment.devicesNumber).map { id ->
            DeviceContext(id, MutableStateFlow(emptyList()))
        }
        val results = contexts.map {
            aggregate(it.id, it.inboundMessages) {
                gradientWithObstacles(sensors[it.id])
            }
        }
        val jobs = results.map { deviceResult ->
            contexts
                .filter { environment.neighbors(deviceResult.localId).contains(it.id) }
                .map { neighborContext ->
                    launch(Dispatchers.Default) {
                        deviceResult.toSend.collect { outboundMessage ->
                            neighborContext.inboundMessages.update { inboundMessages ->
                                inboundMessages.filterNot { it.senderId == deviceResult.localId } + InboundMessage(
                                    deviceResult.localId,
                                    outboundMessage.messages.mapValues { (_, single) ->
                                        single.overrides.getOrElse(neighborContext.id) { single.default }
                                    },
                                )
                            }
                        }
                    }
                }
        }.flatten()
        delay(200)
        jobs.forEach { it.cancelAndJoin() }
        results.forEach { println("${it.localId} -> ${it.result.value}") }
    }

    @Test
    fun runSimulation() {
        runBlocking {
            gradientSimulation()
        }
    }
}
