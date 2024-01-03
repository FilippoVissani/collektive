package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.Collektive.aggregate
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.proactive.networking.OutboundMessage
import it.unibo.collektive.proactive.networking.SingleOutboundMessage
import it.unibo.collektive.reactive.flow.extensions.mapStates
import it.unibo.collektive.stack.Path
import kotlinx.coroutines.flow.StateFlow

class ExchangeTest : StringSpec({

    // device ids
    val id0 = IntId(0)

    // initial values
    val initV1 = 1

    // paths
    val path1 = Path(listOf("invoke.1", "exchange.1"))

    // expected
    val expected2 = 2

    val increaseOrDouble: (StateFlow<Field<Int>>) -> StateFlow<Field<Int>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
        }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            aggregate(id0) {
                val res = exchange(initV1, increaseOrDouble)
                res.value shouldBe OutboundMessage(
                    id0,
                    mapOf(path1 to SingleOutboundMessage(expected2, emptyMap())),
                )
                res
            }
        }
    }
})
