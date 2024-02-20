package it.unibo.collektive.samples

import io.kotest.common.runBlocking
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.operators.share
import it.unibo.collektive.field.min
import it.unibo.collektive.field.plus
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.samples.Environment.Companion.manhattanGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.test.Test

class ReactiveGradient {

    enum class NodeType {
        SOURCE,
        OBSTACLE,
        DEFAULT,
    }

    data class DeviceContext(
        val id: Int,
        val inboundMessages: MutableStateFlow<Iterable<InboundMessage<Int>>>,
    )

    private fun Aggregate<Int>.gradient(nodeType: NodeType): Double =
        share(Double.POSITIVE_INFINITY) { field ->
            when (nodeType) {
                NodeType.SOURCE -> 0.0
                NodeType.OBSTACLE -> Double.POSITIVE_INFINITY
                NodeType.DEFAULT -> (field + 1.0).min(Double.POSITIVE_INFINITY)
            }
        }

    private suspend fun gradientSimulation() = coroutineScope {
        val environment = manhattanGrid(3, 3)
        val contexts = (0..<environment.devicesNumber).map { id ->
            DeviceContext(id, MutableStateFlow(emptyList()))
        }
        val results = contexts.map {
            aggregate(it.id, it.inboundMessages) {
                gradient(
                    when {
                        it.id == 0 -> NodeType.SOURCE
                        it.id % 2 == 0 -> NodeType.OBSTACLE
                        else -> NodeType.DEFAULT
                    },
                )
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
