package it.unibo.collektive.reactive

import it.unibo.collektive.Collektive
import it.unibo.collektive.IntId
import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.network.ReactiveNetworkImplTest
import it.unibo.collektive.network.ReactiveNetworkManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take

class MockSimulator<R>(private val simulation: Map<IntId, AggregateContext.() -> R>) {
    suspend fun runSimulation() = coroutineScope {
        val networkManager = ReactiveNetworkManager()
        val networks = simulation.keys.associateWith { ReactiveNetworkImplTest(networkManager, it) }
        val aggregateResults = simulation.map { (k, v) ->
            k to Collektive.aggregate(k, networks[k]!!, v)
        }.toMap()
        aggregateResults.map { (selfId, aggregateResultFlow) ->
            selfId to
                aggregateResultFlow.take(1).collect {}
        }.toMap()
    }
}
