package it.unibo.collektive.reactive

import it.unibo.collektive.stack.Path
import kotlinx.coroutines.flow.MutableStateFlow

typealias ReactiveState = Map<Path, MutableStateFlow<Any?>>

@Suppress("UNCHECKED_CAST")
internal fun <T> ReactiveState.getTyped(path: Path, default: T): MutableStateFlow<T> {
    return (this[path] as? MutableStateFlow<T>) ?: MutableStateFlow(default).also {
        (this as MutableMap<Path, MutableStateFlow<T>>)[path] = it
    }
}
