package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import it.unibo.collektive.Collektive.aggregate
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExchangeTest : StringSpec({

    // device ids
    val id0 = IntId(0)

    // initial values
    val initV1 = 1

    val increaseOrDouble: (StateFlow<Field<Int>>) -> StateFlow<Field<Int>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
        }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            val aggregateResult = aggregate(id0) {
                val res = exchange(initV1, increaseOrDouble)
                res
            }
            launch(Dispatchers.Default) {
                aggregateResult.result.collect {
                    println("result $it")
                }
            }
            aggregateResult.state.forEach { (path, flow) ->
                launch(Dispatchers.Default) {
                    flow.collect {
                        println("state $path -> $it")
                    }
                }
            }
            aggregateResult.toSend.messages.forEach { (path, flow) ->
                launch(Dispatchers.Default) {
                    flow.collect {
                        println("message $path -> $it")
                    }
                }
            }
        }
    }
})
