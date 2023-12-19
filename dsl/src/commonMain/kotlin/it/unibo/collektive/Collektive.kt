package it.unibo.collektive

import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.aggregate.AggregateResult
import it.unibo.collektive.flow.extensions.combineStates
import it.unibo.collektive.flow.extensions.mapStates
import it.unibo.collektive.networking.InboundMessage
import it.unibo.collektive.state.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Create a Collektive device with a specific [id] and a [network] to manage incoming and outgoing messages,
 * the [computeFunction] is the function to apply within the [AggregateContext].
 */
class Collektive<R>(
    val id: ID,
) {

    /**
     * The [State] of the Collektive device.
     */
    val state: MutableStateFlow<State> = MutableStateFlow(emptyMap())

    /**
     * Apply once the aggregate function to the parameters of the device,
     * then returns the result of the computation.
     */
/*    fun cycle(): R = executeRound().result*/

    /**
     * Apply the aggregate function to the parameters of the device while the [condition] is satisfied,
     * then returns the result of the computation.
     */
/*    fun cycleWhile(condition: (AggregateResult<R>) -> Boolean): R {
        var compute = executeRound()
        while (condition(compute)) {
            compute = executeRound()
        }
        return compute.result
    }*/

/*    private fun executeRound(): AggregateResult<R> {
        val result = aggregate(id, network, state, computeFunction)
        state = result.newState
        return result
    }*/

    companion object {

        /**
         * Aggregate program entry point which computes an iteration of a device [localId], taking as parameters
         * the previous [state], the [messages] received from the neighbours and the [compute] with AggregateContext
         * object receiver that provides the aggregate constructs.
         */
        fun <R> aggregate(
            localId: ID,
            inbound: StateFlow<InboundMessage>,
            compute: AggregateContext.() -> StateFlow<R>,
        ): StateFlow<AggregateResult<R>> = with(AggregateContext(localId)) {
            val result: StateFlow<AggregateResult<R>> = combineStates(compute(), inbound) { computationResult, message ->
                this.receiveMessage(message)
                AggregateResult(localId, computationResult, outboundMessages.value, states.value)
            }
            result
        }
    }
}
