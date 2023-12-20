package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import it.unibo.collektive.stack.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class Test  : StringSpec({

    // device ids
    val id0 = IntId(0)

    // initial values
    val initV1 = 1

    // paths
    val path1 = Path(listOf("invoke.1", "exchange.1"))

    // expected
    val expected2 = 2

    val increaseOrDouble: (Field<Int>) -> Field<Int> = { f ->
        f.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            val result = aggregate(id0, MutableStateFlow(emptyList())) {
                val res = exchange(initV1, increaseOrDouble)
                res.localValue shouldBe expected2
                messagesToSend() shouldBe OutboundMessage(
                    id0,
                    mapOf(path1 to SingleOutboundMessage(expected2, emptyMap())),
                )
            }
            launch(Dispatchers.Default) {
                result.collect { aggregateResult ->
                    println(aggregateResult)
                }
            }
        }
    }
})
