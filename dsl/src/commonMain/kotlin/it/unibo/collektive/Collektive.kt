package it.unibo.collektive

import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.aggregate.AggregateResult
import it.unibo.collektive.flow.extensions.mapStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.Network
import it.unibo.collektive.networking.ReactiveNetwork
import it.unibo.collektive.state.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Create a Collektive device with a specific [id] and a [network] to manage incoming and outgoing messages,
 * the [computeFunction] is the function to apply within the [AggregateContext].
 */
class Collektive<R>(
    val id: ID,
    private val network: Network,
    private val computeFunction: AggregateContext.() -> R,
) {

    /**
     * The [State] of the Collektive device.
     */
    var state: State = emptyMap()
        private set

    /**
     * Apply once the aggregate function to the parameters of the device,
     * then returns the result of the computation.
     */
    fun cycle(): R = executeRound().result

    /**
     * Apply the aggregate function to the parameters of the device while the [condition] is satisfied,
     * then returns the result of the computation.
     */
    fun cycleWhile(condition: (AggregateResult<R>) -> Boolean): R {
        var compute = executeRound()
        while (condition(compute)) {
            compute = executeRound()
        }
        return compute.result
    }

    private fun executeRound(): AggregateResult<R> {
        val result = aggregate(id, network, state, computeFunction)
        state = result.newState
        return result
    }

    companion object {

        /**
         * Aggregate program entry point which computes an iteration of a device [localId], taking as parameters
         * the previous [state], the [messages] received from the neighbours and the [compute] with AggregateContext
         * object receiver that provides the aggregate constructs.
         */
        fun <R> aggregate(
            localId: ID,
            inbound: Iterable<InboundMessage> = emptySet(),
            previousState: State = emptyMap(),
            compute: AggregateContext.() -> R,
        ): AggregateResult<R> = AggregateContext(localId, inbound, previousState).run {
            AggregateResult(localId, compute(), messagesToSend(), newState())
        }

        /**
         * Aggregate program entry point which computes an iterations of a device [localId],
         * over a [network] of devices, with the lambda [init] with AggregateContext
         * object receiver that provides the aggregate constructs.
         */
        fun <R> aggregate(
            localId: ID,
            network: Network,
            previousState: State = emptyMap(),
            compute: AggregateContext.() -> R,
        ): AggregateResult<R> = with(AggregateContext(localId, network.read(), previousState)) {
            AggregateResult(localId, compute(), messagesToSend(), newState()).also {
                network.write(it.toSend)
            }
        }

        /**
         * TODO.
         */
        fun <R> aggregate(
            localId: ID,
            inbound: StateFlow<Iterable<InboundMessage>>,
            compute: AggregateContext.() -> R,
        ): StateFlow<AggregateResult<R>> {
            val states = MutableStateFlow<State>(emptyMap())
            val contextFlow = mapStates(inbound) {
                AggregateContext(localId, it, states.value)
            }
            val result: StateFlow<AggregateResult<R>> = mapStates(contextFlow) { aggregateContext ->
                aggregateContext.run {
                    val aggregateResult = AggregateResult(localId, compute(), messagesToSend(), newState())
                    states.update { aggregateResult.newState }
                    aggregateResult
                }
            }
            return result
        }

        /**
         * TODO.
         */
        suspend fun <R> aggregate(
            localId: ID,
            network: ReactiveNetwork,
            compute: AggregateContext.() -> R,
        ): StateFlow<AggregateResult<R>> = coroutineScope {
            val contextFlow = MutableStateFlow(AggregateContext(localId, emptySet(), emptyMap()))

            val job = launch(Dispatchers.Default) {
                network.read().collect { messages ->
                    contextFlow.update {
                        AggregateContext(localId, messages, it.newState())
                    }
                }
            }

            val result: StateFlow<AggregateResult<R>> = mapStates(contextFlow) { aggregateContext ->
                aggregateContext.run {
                    val aggregateResult = AggregateResult(localId, compute(), messagesToSend(), newState())
                    network.write(aggregateResult.toSend)
                    aggregateResult
                }
            }

            delay(50)
            job.cancelAndJoin()
            result
        }
    }
}
