package it.unibo.collektive.reactive.flow.extensions

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private class CombinedStateFlow<T>(
    private val getValue: () -> T,
    private val flow: Flow<T>,
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
    flow: Flow<T>,
): StateFlow<T> = CombinedStateFlow(getValue, flow)

/**
 * TODO.
 *
 * @param T
 * @param R
 * @param flow
 * @param transform
 */
fun <T, R> StateFlow<T>.mapStates(
    transform: (T) -> R,
) = combineStates(
    getValue = { transform(this.value) },
    flow = this.map { value -> transform(value) },
)

/**
 * Combines [stateFlow1] and [stateFlow2] and transforms them into another [StateFlow] with [transform].
 */
fun <T1, T2, R> combineStates(
    stateFlow1: StateFlow<T1>,
    stateFlow2: StateFlow<T2>,
    transform: (T1, T2) -> R,
): StateFlow<R> = combineStates(
    getValue = { transform(stateFlow1.value, stateFlow2.value) },
    flow = combine(stateFlow1, stateFlow2) { value1, value2 -> transform(value1, value2) },
)

/**
 * TODO.
 *
 * @param T1
 * @param T2
 * @param T3
 * @param R
 * @param stateFlow1
 * @param stateFlow2
 * @param stateFlow3
 * @param transform
 * @return
 */
fun <T1, T2, T3, R> combineStates(
    stateFlow1: StateFlow<T1>,
    stateFlow2: StateFlow<T2>,
    stateFlow3: StateFlow<T3>,
    transform: (T1, T2, T3) -> R,
): StateFlow<R> = combineStates(
    getValue = { transform(stateFlow1.value, stateFlow2.value, stateFlow3.value) },
    flow = combine(stateFlow1, stateFlow2, stateFlow3) { value1, value2, value3 -> transform(value1, value2, value3) },
)

/**
 * TODO.
 *
 * @param T
 * @return
 */
@OptIn(DelicateCoroutinesApi::class)
fun <T> StateFlow<StateFlow<T>>.flattenConcat(): StateFlow<T> {
    val result = MutableStateFlow(value.value)
    this.onEach { innerFlow ->
        innerFlow.onEach { value ->
            result.update { value }
        }.launchIn(GlobalScope)
    }.launchIn(GlobalScope)
    return result
}
