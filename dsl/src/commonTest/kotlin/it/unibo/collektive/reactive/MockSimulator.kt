package it.unibo.collektive.reactive

import it.unibo.collektive.Collektive
import it.unibo.collektive.IntId
import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.network.ReactiveNetworkImplTest
import it.unibo.collektive.network.ReactiveNetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MockSimulator<R>(val simulation: Map<IntId, AggregateContext.() -> R>) {
    suspend fun runSimulation() = coroutineScope {
        val networkManager = ReactiveNetworkManager()
        val networks = simulation.keys.associateWith { ReactiveNetworkImplTest(networkManager, it) }
        val aggregateResults = simulation.map { (k, v) ->
            k to Collektive.aggregate(k, networks[k]!!, v)
        }.toMap()
        val jobs = aggregateResults.map { (selfId, aggregateResultFlow) ->
            selfId to
                launch(Dispatchers.Default) {
                    aggregateResultFlow.collect {
                        println("$selfId -> ${it.result}")
                    }
                }
        }.toMap()
        delay(50)
        jobs.values.forEach { it.cancelAndJoin() }
    }
}
