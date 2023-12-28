package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.IntId
import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.field.Field
import it.unibo.collektive.network.ReactiveNetworkImplTest
import it.unibo.collektive.network.ReactiveNetworkManager
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import it.unibo.collektive.stack.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Test : StringSpec({

    // device ids
    val id0 = IntId(0)
    val id1 = IntId(1)
    val id2 = IntId(2)
    val id3 = IntId(3)

    // initial values
    val initV1 = 1
    val initV3 = 3
    val initV5 = 5

    // paths
    val path1 = Path(listOf("invoke.1", "exchange.1"))

    // expected
    val expected2 = 2

    val increaseOrDouble: (Field<Int>) -> Field<Int> = { f ->
        f.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
    }

    suspend fun <R> runSimulation(simulation: Map<IntId, AggregateContext.() -> R>) = coroutineScope {
        val networkManager = ReactiveNetworkManager()
        val networks = simulation.keys.associateWith { ReactiveNetworkImplTest(networkManager, it) }
        val aggregateResults = simulation.map { (k, v) ->
            k to aggregate(k, networks[k]!!, v)
        }.toMap()
        val jobs = aggregateResults.map { (selfId, aggregateResultFlow) ->
            selfId to
                launch(Dispatchers.Default) {
                    aggregateResultFlow.collect { aggregateResult ->
                        println("$selfId -> ${aggregateResult.result}")
                    }
                }
        }.toMap()
        delay(100)
        jobs.values.forEach { it.cancelAndJoin() }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            runSimulation(
                mapOf(
                    id0 to {
                        val res = exchange(initV1, increaseOrDouble)
                        res.localValue shouldBe expected2
                        messagesToSend() shouldBe OutboundMessage(
                            id0,
                            mapOf(path1 to SingleOutboundMessage(expected2, emptyMap())),
                        )
                    },
                )
            )
        }
    }

    "Exchange should work between three aligned devices" {
        runBlocking {
            runSimulation(
                mapOf(
                    // Device 1
                    id1 to {
                        exchange(initV1, increaseOrDouble)
                    },
                    // Device 2
                    id2 to {
                        exchange(initV3, increaseOrDouble)
                    },
                    // Device 3
                    id3 to {
                        exchange(initV5, increaseOrDouble)
                    },
                )
            )
        }
    }
})
