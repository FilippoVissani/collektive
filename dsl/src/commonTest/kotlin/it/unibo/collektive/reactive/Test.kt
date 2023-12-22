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

    // device ids
    val id1 = IntId(1)
    val id2 = IntId(2)
    val id3 = IntId(3)

    // initial values
    val initialValue = 100

    val increaseOrDouble: (Field<Int>) -> Field<Int> = { f ->
        f.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
    }

    suspend fun <R> runSimulation(ids: Iterable<IntId>, program: AggregateContext.() -> R) = coroutineScope {
        println("#################################")
        val inboundMessages = ids.associateWith { MutableStateFlow(emptyList<InboundMessage>()) }
        val aggregateResults = ids.associateWith {
            aggregate(it, inboundMessages[it]!!.asStateFlow(), program)
        }
        val jobs = aggregateResults.map { (selfId, aggregateResultFlow) ->
            selfId to
                launch(Dispatchers.Default) {
                    aggregateResultFlow.collect { aggregateResult ->
                        println("$aggregateResult")
                        inboundMessages.filter { (k, _) -> k != selfId }.values.forEach { neighborsInboundMessages ->
                            neighborsInboundMessages.update { messages ->
                                messages.filter { it.senderId != selfId } +
                                    InboundMessage(
                                        aggregateResult.toSend.senderId,
                                        aggregateResult.toSend.messages.mapValues { (_, single) ->
                                            single.overrides.getOrElse(aggregateResult.toSend.senderId) { single.default }
                                        },
                                    )
                            }
                        }
                    }
                }
        }.toMap()
        delay(50)
        jobs.forEach { it.value.cancelAndJoin() }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            runSimulation(listOf(id1)) {
                exchange(initialValue, increaseOrDouble)
            }
        }
    }

    "Exchange should work between three aligned devices" {
        runBlocking {
            runSimulation(listOf(id1, id2, id3)) {
                exchange(initialValue, increaseOrDouble)
            }
        }
    }
})
