package it.unibo.collektive.samples

import io.kotest.common.runBlocking
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.networking.InboundMessage
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
        val inboundMessages: MutableStateFlow<Iterable<InboundMessage<Int>>>,
    )

    private suspend fun gradientSimulation() = coroutineScope {
        val contexts = (0..<environment.devicesNumber).map { id ->
            DeviceContext(id, MutableStateFlow(emptyList()))
        }
        val results = contexts.map {
            aggregate(it.id, it.inboundMessages) {
                aggregateProgram(it.id)
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
                                        result.toSend.messages.mapValues { (_, single) ->
                                            single.overrides.getOrElse(neighborId) { single.default }
                                        },
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
