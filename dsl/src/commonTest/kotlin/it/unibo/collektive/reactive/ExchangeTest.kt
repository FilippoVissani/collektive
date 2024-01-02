package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.Collektive.Companion.aggregate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExchangeTest : StringSpec({

    // device ids
    val id0 = IntId(0)

    // initial values
    val initV1 = 1

    val increaseOrDouble: (Field<Int>) -> Field<Int> = { field ->
        field.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            val result: AggregateExpression<Field<Int>> = aggregate(id0) {
                exchange(initV1, increaseOrDouble)
            }
            launch(Dispatchers.Default) {
                result.compute().collect { message ->
                    println(message.messages)
                }
            }
        }
    }
})
