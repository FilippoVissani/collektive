package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import it.unibo.collektive.reactive.RCollektive.Companion.aggregate
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RRepeatTest : StringSpec({

    // device ids
    val id0 = 0

    // initial values
    val init = 0

    val increase: (StateFlow<Int>) -> StateFlow<Int> = { flow ->
        flow.mapStates { it + 1 }
    }

    "First time rExchange should return the initial value" {
        runBlocking {
            val aggregateResult = aggregate(id0) {
                rRepeat(init, increase)
            }
            val job = launch(Dispatchers.Default) {
                runSimulation(mapOf(aggregateResult to MutableStateFlow(emptyList())))
            }
            delay(100)
            job.cancelAndJoin()
            aggregateResult.result.value shouldBeGreaterThan 0
        }
    }
})
