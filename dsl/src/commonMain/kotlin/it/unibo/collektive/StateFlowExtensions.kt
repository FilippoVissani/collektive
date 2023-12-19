package it.unibo.collektive

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private class CombinedStateFlow<T>(
    private val getValue: () -> T,
    private val flow: Flow<T>
) : StateFlow<T> {

    override val replayCache: List<T> get() = listOf(value)

    override val value: T get() = getValue()

    override suspend fun collect(collector: FlowCollector<T>): Nothing =
        coroutineScope { flow.stateIn(this).collect(collector) }
}

/**
 * Returns [StateFlow] from [flow] having initial value from calculation of [getValue].
 */
fun <T> combineStates(
    getValue: () -> T,
    flow: Flow<T>
): StateFlow<T> = CombinedStateFlow(getValue, flow)

/**
 * TODO.
 */
fun <T, R> mapStates(
    flow: StateFlow<T>,
    transform: (T) -> R
) = combineStates(
    getValue = { transform(flow.value) },
    flow = flow.map { value -> transform(value) }
)

/**
 * Combines [stateFlow1] and [stateFlow2] and transforms them into another [StateFlow] with [transform].
 */
fun <T1, T2, R> combineStates(
    stateFlow1: StateFlow<T1>,
    stateFlow2: StateFlow<T2>,
    transform: (T1, T2) -> R
): StateFlow<R> = combineStates(
    getValue = { transform(stateFlow1.value, stateFlow2.value) },
    flow = combine(stateFlow1, stateFlow2) { value1, value2 -> transform(value1, value2) }
)
