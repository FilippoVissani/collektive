package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.IntId
import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.field.Field
import it.unibo.collektive.networking.InboundMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class Test : StringSpec({

    val id1 = IntId(1)
    val id2 = IntId(2)
    val id3 = IntId(3)

    // initial values
    val initV1 = 1
    val initV2 = 2
    val initV3 = 3

    val increaseOrDouble: (Field<Int>) -> Field<Int> = { f ->
        f.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
    }

    suspend fun <R> runSimulation(simulation: Map<IntId, AggregateContext.() -> R>) = coroutineScope {
        println("#################################")
        val inboundMessages = simulation.map { (k, _) -> k to MutableStateFlow(emptyList<InboundMessage>()) }.toMap()
        val aggregateResults = simulation.map { (k, v) ->
            k to aggregate(k, inboundMessages[k]!!.asStateFlow(), v)
        }
        val jobs = aggregateResults.associate { (selfId, aggregateResultFlow) ->
            selfId to
                launch(Dispatchers.Default) {
                    aggregateResultFlow.collect { aggregateResult ->
                        println("${aggregateResult.localId} = ${aggregateResult.result}")
                        inboundMessages.filter { (k, _) -> k != selfId }.values.forEach { neighborsInboundMessages ->
                            neighborsInboundMessages.update { inboundMessages ->
                                val message = InboundMessage(
                                    aggregateResult.toSend.senderId,
                                    aggregateResult.toSend.messages.mapValues { (_, single) ->
                                        single.overrides.getOrElse(aggregateResult.toSend.senderId) { single.default }
                                    },
                                )
                                inboundMessages.filter { it.senderId != selfId } + message
                            }
                        }
                    }
                }
        }
        delay(50)
        jobs.forEach { it.value.cancelAndJoin() }
    }

    fun exchangeProgram(value: Int): AggregateContext.() -> Field<Int> {
        return { exchange(value, increaseOrDouble) }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            runSimulation(
                mapOf(
                    id1 to exchangeProgram(initV1),
                )
            )
        }
    }

    "Exchange should work between three aligned devices" {
        runBlocking {
            runSimulation(
                mapOf(
                    id1 to exchangeProgram(initV1),
                    id2 to exchangeProgram(initV2),
                    id3 to exchangeProgram(initV3),
                )
            )
        }
    }
})
