
package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.Collektive.Companion.aggregate
import it.unibo.collektive.field.Field
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn

@OptIn(DelicateCoroutinesApi::class)
class AlignmentTest : StringSpec({

    // device ids
    val id0 = 0
    val id1 = 1
    val id2 = 2

    val mockFunction: (Field<Int, String>) -> Field<Int, String> = { f ->
        f.mapWithId { _, _ -> "mockFunction" }
    }

    "Devices should be aligned" {
        runBlocking {
            val networkManager = MockReactiveNetworkManager()
            val expectedResult = mapOf(
                id0 to "mockFunction",
                id1 to "mockFunction",
                id2 to "mockFunction",
            )

            // Device 0
            val network0 = MockReactiveNetwork(networkManager, id0)
            val result0 = aggregate(id0, network0) {
                exchange("initial", mockFunction)
            }

            // Device 1
            val network1 = MockReactiveNetwork(networkManager, id1)
            val result1 = aggregate(id1, network1) {
                exchange("initial", mockFunction)
            }

            // Device 2
            val network2 = MockReactiveNetwork(networkManager, id2)
            val result2 = aggregate(id2, network2) {
                exchange("initial", mockFunction)
            }
            result0.launchIn(GlobalScope)
            result1.launchIn(GlobalScope)
            result2.launchIn(GlobalScope)
            delay(200)
            result0.value.result.toMap() shouldBe expectedResult
            result1.value.result.toMap() shouldBe expectedResult
            result2.value.result.toMap() shouldBe expectedResult
        }
    }
})
