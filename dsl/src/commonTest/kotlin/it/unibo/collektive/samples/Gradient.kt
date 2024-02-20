package it.unibo.collektive.samples

import io.kotest.common.runBlocking
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.operators.share
import it.unibo.collektive.field.min
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.samples.Environment.Companion.manhattanGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.test.Test

class Gradient {

    data class DeviceContext(val id: Int, val inboundMessages: MutableStateFlow<Iterable<InboundMessage<Int>>>)

    private fun Aggregate<Int>.gradient(source: Boolean): Double =
        share(Double.POSITIVE_INFINITY) { field ->
            when {
                source -> 0.0
                else -> (field.map { it + 1 }).min(Double.POSITIVE_INFINITY)
            }
        }

    private suspend fun gradientSimulation() = coroutineScope {
        val env = manhattanGrid(3, 3)
        val contexts = (0..<env.devicesNumber).map { id ->
            DeviceContext(id, MutableStateFlow(emptyList()))
        }
        val results = contexts.map {
            aggregate(it.id, it.inboundMessages) {
                gradient(it.id == 4)
            }
        }
        results.forEach { resultFlow ->
            launch(Dispatchers.Default) {
                resultFlow.collect { result ->
                    println("${result.localId} -> ${result.result}")
                    env.neighbors(result.localId)
                        .filterNot { it == result.localId }
                        .forEach { neighbor ->
                            contexts
                                .first { it.id == neighbor }
                                .inboundMessages
                                .update { inboundMessages ->
                                    inboundMessages
                                        .filter { it.senderId == result.localId } + InboundMessage(
                                        result.localId,
                                        result.toSend.messages.mapValues { (_, single) ->
                                            single.overrides.getOrElse(neighbor) { single.default }
                                        },
                                    )
                                }
                        }
                }
            }
        }
    }

    @Test
    fun runSimulation() {
        runBlocking {
            gradientSimulation()
        }
    }
}
