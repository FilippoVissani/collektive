package it.unibo.collektive.reactive

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.Collektive
import it.unibo.collektive.IntId
import it.unibo.collektive.field.Field
import it.unibo.collektive.proactive.networking.InboundMessage
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BranchTest : StringSpec({

    // device ids
    val id0 = IntId(0)
    val id1 = IntId(1)

    val trueBranch = "trueBranch"
    val falseBranch = "falseBranch"

    val trueFunction: (StateFlow<Field<String>>) -> StateFlow<Field<String>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> "trueBranch" }
        }
    }

    val falseFunction: (StateFlow<Field<String>>) -> StateFlow<Field<String>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, _ -> "falseBranch" }
        }
    }

    suspend fun runSingleBranchProgram(
        initialCondition0: Boolean,
        initialCondition1: Boolean,
        finalCondition0: Boolean = initialCondition0,
        finalCondition1: Boolean = initialCondition1,
        assertion0: (ReactiveAggregateResult<Field<String>>) -> Unit = {},
        assertion1: (ReactiveAggregateResult<Field<String>>) -> Unit = {},
    ) = coroutineScope {
        val channel0: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
        val channel1: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
        val reactiveBoolean0 = MutableStateFlow(initialCondition0)
        val reactiveBoolean1 = MutableStateFlow(initialCondition1)

        val aggregateResult0 = Collektive.aggregate(id0, channel0) {
            branch(
                { reactiveBoolean0 },
                { exchange("initial", trueFunction) },
                { exchange("initial", falseFunction) },
            )
        }

        val aggregateResult1 = Collektive.aggregate(id1, channel1) {
            branch(
                { reactiveBoolean1 },
                { exchange("initial", trueFunction) },
                { exchange("initial", falseFunction) },
            )
        }

        val job = launch(Dispatchers.Default) {
            runSimulation(
                mapOf(
                    aggregateResult0 to channel0,
                    aggregateResult1 to channel1,
                )
            )
        }
        delay(200)
        reactiveBoolean0.update { finalCondition0 }
        reactiveBoolean1.update { finalCondition1 }
        delay(200)
        job.cancelAndJoin()
        assertion0(aggregateResult0)
        assertion1(aggregateResult1)
    }

    "Devices with same condition should be aligned" {
        runBlocking {
            runSingleBranchProgram(
                initialCondition0 = true,
                initialCondition1 = true,
                assertion0 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(0) to trueBranch, IntId(1) to trueBranch) },
                assertion1 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(0) to trueBranch, IntId(1) to trueBranch) },
            )
        }
    }

    "Devices with different conditions should not be aligned" {
        runBlocking {
            runSingleBranchProgram(
                initialCondition0 = true,
                initialCondition1 = false,
                assertion0 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(0) to trueBranch) },
                assertion1 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(1) to falseBranch) },
            )
        }
    }

    "If the condition becomes the same the two devices should align" {
        runBlocking {
            runSingleBranchProgram(
                initialCondition0 = true,
                initialCondition1 = false,
                finalCondition0 = true,
                finalCondition1 = true,
                assertion0 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(0) to trueBranch, IntId(1) to trueBranch) },
                assertion1 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(0) to trueBranch, IntId(1) to trueBranch) },
            )
        }
    }

    "If the condition becomes different the two devices should not align" {
        runBlocking {
            runSingleBranchProgram(
                initialCondition0 = true,
                initialCondition1 = true,
                finalCondition0 = true,
                finalCondition1 = false,
                assertion0 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(0) to trueBranch) },
                assertion1 = { aggregateResult -> aggregateResult.result.value.toMap() shouldBe mapOf(IntId(1) to falseBranch) },
            )
        }
    }

    "Multiple nested Branch should work" {
        val channel0: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
        val channel1: MutableStateFlow<List<InboundMessage>> = MutableStateFlow(emptyList())
        val reactiveBoolean0 = MutableStateFlow(true)
        val reactiveBoolean1 = MutableStateFlow(true)

        val aggregateResult0 = Collektive.aggregate(id0, channel0) {
            branch(
                { reactiveBoolean0 },
                {
                    branch(
                        { reactiveBoolean0 },
                        { exchange("initial", trueFunction) },
                        { exchange("initial", falseFunction) }
                    )
                },
                {
                    branch(
                        { reactiveBoolean0 },
                        { exchange("initial", trueFunction) },
                        { exchange("initial", falseFunction) }
                    )
                },
            )
        }

        val aggregateResult1 = Collektive.aggregate(id1, channel1) {
            branch(
                { reactiveBoolean1 },
                {
                    branch(
                        { reactiveBoolean1 },
                        { exchange("initial", trueFunction) },
                        { exchange("initial", falseFunction) }
                    )
                },
                {
                    branch(
                        { reactiveBoolean1 },
                        { exchange("initial", trueFunction) },
                        { exchange("initial", falseFunction) }
                    )
                },
            )
        }

        val job = launch(Dispatchers.Default) {
            runSimulation(
                mapOf(
                    aggregateResult0 to channel0,
                    aggregateResult1 to channel1,
                )
            )
        }
        delay(200)
        reactiveBoolean0.update { false }
        reactiveBoolean1.update { false }
        delay(200)
        job.cancelAndJoin()
        aggregateResult0.result.value.toMap() shouldBe mapOf(IntId(0) to falseBranch, IntId(1) to falseBranch)
        aggregateResult1.result.value.toMap() shouldBe mapOf(IntId(0) to falseBranch, IntId(1) to falseBranch)
    }
})
