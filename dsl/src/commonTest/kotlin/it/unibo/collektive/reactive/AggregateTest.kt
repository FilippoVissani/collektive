package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.Collektive
import it.unibo.collektive.field.Field
import kotlinx.coroutines.flow.MutableStateFlow

class AggregateTest : StringSpec({

    // device ids
    val id0 = 0

    val mockFunction: (Field<Int, String>) -> Field<Int, String> = { f ->
        f.mapWithId { _, _ -> "mockFunction" }
    }

    "Aggregate program should be executed correctly" {
        runBlocking {
            val result = Collektive.aggregate(id0, MutableStateFlow(emptyList())) {
                exchange("initial", mockFunction)
            }
            result.value.result.toMap() shouldBe mapOf(id0 to "mockFunction")
        }
    }
})
