package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.networking.OutboundMessage
import it.unibo.collektive.networking.SingleOutboundMessage
import it.unibo.collektive.stack.Path

class ExchangeTest : StringSpec({

    // device ids
    val id0 = IntId(0)
    val id1 = IntId(1)
    val id2 = IntId(2)
    val id3 = IntId(3)

    // initial values
    val initV1 = 1
    val initV2 = 2
    val initV3 = 3
    val initV4 = 4
    val initV5 = 5
    val initV6 = 6

    // paths
    val path1 = Path(listOf("invoke.1", "exchange.1"))
    val path2 = Path(listOf("invoke.1", "exchange.2"))

    // expected
    val expected2 = 2
    val expected3 = 3
    val expected5 = 5
    val expected6 = 6
    val expected7 = 7
    val expected10 = 10

    val increaseOrDouble: (Field<Int>) -> Field<Int> = { f ->
        f.mapWithId { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
    }

    "First time exchange should return the initial value" {
        runBlocking {
            val simulator = MockSimulator(
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
            simulator.runSimulation()
        }
    }

    "Exchange should work between three aligned devices" {
        runBlocking {
            val simulator = MockSimulator(
                mapOf(
                    // Device 1
                    id1 to {
                        val res1 = exchange(initV1, increaseOrDouble)
                        val res2 = exchange(initV2, increaseOrDouble)
                        res1.localValue shouldBe expected2
                        res2.localValue shouldBe expected3
                        messagesToSend() shouldBe OutboundMessage(
                            id1,
                            mapOf(
                                path1 to SingleOutboundMessage(expected2, emptyMap()),
                                path2 to SingleOutboundMessage(expected3, emptyMap()),
                            ),
                        )
                    },
                    // Device 2
                    id2 to {
                        val res1 = exchange(initV3, increaseOrDouble)
                        val res2 = exchange(initV4, increaseOrDouble)

                        res1.localValue shouldBe expected6
                        res2.localValue shouldBe expected5
                        messagesToSend() shouldBe OutboundMessage(
                            id2,
                            mapOf(
                                path1 to SingleOutboundMessage(
                                    expected6,
                                    mapOf(id1 to expected3),
                                ),
                                path2 to SingleOutboundMessage(
                                    expected5,
                                    mapOf(id1 to expected6),
                                ),
                            ),
                        )
                    },
                    // Device 3
                    id3 to {
                        val res1 = exchange(initV5, increaseOrDouble)
                        val res2 = exchange(initV6, increaseOrDouble)

                        res1.localValue shouldBe expected10
                        res2.localValue shouldBe expected7
                        messagesToSend() shouldBe OutboundMessage(
                            id3,
                            mapOf(
                                path1 to SingleOutboundMessage(
                                    expected10,
                                    mapOf(
                                        id1 to expected3,
                                        id2 to expected7,
                                    ),
                                ),
                                path2 to SingleOutboundMessage(
                                    expected7,
                                    mapOf(
                                        id1 to expected6,
                                        id2 to expected10,
                                    ),
                                ),
                            ),
                        )
                    },
                )
            )
            simulator.runSimulation()
        }
    }
})
