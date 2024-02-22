package it.unibo.collektive.reactive

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.field.Field
import it.unibo.collektive.reactive.flow.extensions.combineStates
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.flow.StateFlow

/**
 * TODO.
 *
 * @param ID
 * @param Initial
 * @param initial
 * @param transform
 * @return
 */
fun <ID : Any, Initial> Aggregate<ID>.rShare(
    initial: Initial,
    transform: (StateFlow<Field<ID, Initial>>) ->
    StateFlow<Initial>,
): StateFlow<Initial> =
    rExchange(initial) { fieldFlow ->
        combineStates(fieldFlow, transform(fieldFlow)) { f, t ->
            f.map { t }
        }
    }.mapStates { it.localValue }
