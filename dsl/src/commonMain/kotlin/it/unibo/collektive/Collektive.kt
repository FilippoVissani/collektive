package it.unibo.collektive

import it.unibo.collektive.aggregate.AggregateResult
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.impl.AggregateContext
import it.unibo.collektive.flow.extensions.mapStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.networking.Network
import it.unibo.collektive.state.State
import kotlinx.coroutines.flow.StateFlow

/**
 * Create a Collektive device with a specific [localId] and a [network] to manage incoming and outgoing messages,
 * the [computeFunction] is the function to apply within the [AggregateContext].
 */
class Collektive<ID : Any, R>(
    val localId: ID,
    private val network: Network<ID>,
    private val computeFunction: Aggregate<ID>.() -> R,
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
    fun cycleWhile(condition: (AggregateResult<ID, R>) -> Boolean): R {
        var compute = executeRound()
        while (condition(compute)) {
            compute = executeRound()
        }
        return compute.result
    }

    private fun executeRound(): AggregateResult<ID, R> {
        val result = aggregate(localId, network, state, computeFunction)
        state = result.newState
        return result
    }

    companion object {
        /**
         * Aggregate program entry point which computes an iteration of a device [localId], taking as parameters
         * the previous [state], the [messages] received from the neighbours and the [compute] with AggregateContext
         * object receiver that provides the aggregate constructs.
         */
        fun <ID : Any, R> aggregate(
            localId: ID,
            previousState: State = emptyMap(),
            inbound: Iterable<InboundMessage<ID>> = emptySet(),
            compute: Aggregate<ID>.() -> R,
        ): AggregateResult<ID, R> = AggregateContext(localId, inbound, previousState).run {
            AggregateResult(localId, compute(), messagesToSend(), newState())
        }

        /**
         * Aggregate program entry point which computes an iteration of a device [localId],
         * over a [network] of devices, optionally from a [previousState],
         * running the [compute] aggregate program.
         */
        fun <ID : Any, R> aggregate(
            localId: ID,
            network: Network<ID>,
            previousState: State = emptyMap(),
            compute: Aggregate<ID>.() -> R,
        ): AggregateResult<ID, R> = with(AggregateContext(localId, network.read(), previousState)) {
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
            val contextFlow: StateFlow<AggregateContext> = mapStates(inbound) { messages ->
                AggregateContext(localId, messages, emptyMap())
            }
            val result: StateFlow<AggregateResult<R>> = mapStates(contextFlow) { aggregateContext ->
                aggregateContext.run {
                    AggregateResult(localId, compute(), messagesToSend(), newState())
                }
            }
            return result
        }
    }
}
