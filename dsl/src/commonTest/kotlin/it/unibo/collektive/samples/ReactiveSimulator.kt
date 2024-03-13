package it.unibo.collektive.samples

import io.kotest.common.runBlocking
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.flow.extensions.combineStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.state.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.test.Test

class ReactiveSimulator {

    data class DeviceContext(
        val id: Int,
        val previousState: MutableStateFlow<State>,
        val inboundMessages: MutableStateFlow<Iterable<InboundMessage<Int>>>,
        val sensor: MutableStateFlow<NodeType>,
    )

    private suspend fun gradientSimulation() = coroutineScope {
        val contexts = (0..<environment.devicesNumber).map { id ->
            DeviceContext(id, MutableStateFlow(emptyMap()), MutableStateFlow(emptyList()), reactiveSensors[id])
        }
        val results = contexts.map { deviceContext ->
            combineStates(deviceContext.inboundMessages, deviceContext.sensor) { inboundValue, sensorValue ->
                aggregate(deviceContext.id, deviceContext.previousState.value, inboundValue) {
                    gradientWithObstacles(sensorValue)
                }.also { aggregateResult ->
                    deviceContext.previousState.update { aggregateResult.newState }
                }
            }
        }
        val jobs = results.map { resultFlow ->
            launch(Dispatchers.Default) {
                resultFlow.collect { result ->
                    environment.neighbors(result.localId)
                        .forEach { neighborId ->
                            contexts
                                .first { it.id == neighborId }
                                .inboundMessages
                                .update { inboundMessages ->
                                    inboundMessages
                                        .filterNot { it.senderId == result.localId } + InboundMessage(
                                        result.localId,
                                        result.toSend.messagesFor(neighborId),
                                    )
                                }
                        }
                }
            }
        }
        delay(200)
        jobs.forEach { it.cancelAndJoin() }
        results.forEach { println("${it.value.localId} -> ${it.value.result}") }
    }

    @Test
    fun runSimulation() {
        runBlocking {
            gradientSimulation()
        }
    }
}
