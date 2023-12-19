package it.unibo.collektive.aggregate.ops

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.some
import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.field.Field
import it.unibo.collektive.flow.extensions.combineStates
import it.unibo.collektive.flow.extensions.mapStates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Observes the value of an expression [type] across neighbours.
 *
 * ## Example
 * ```
 * val field = neighbouring(0)
 * ```
 * The field returned has as local value the value passed as input (0 in this example).
 * ```
 * val field = neighbouring({ 2 * 2 })
 * ```
 * In this case, the field returned has the result of the computation as local value.
 */
suspend fun <R> AggregateContext.neighbouring(type: R): StateFlow<Field<R>> {
    val body: (StateFlow<Field<R>>) -> StateFlow<Field<R>> = { flow -> mapStates(flow) { it.mapWithId { _, x -> x } } }
    return exchange(type, body)
}

/**
 * [sharing] captures the space-time nature of field computation through observation of neighbours' values, starting
 * from an [initial] value, it reduces to a single local value given a [transform] function and updating and sharing
 * to neighbours of a local variable.
 * ```
 * val result = sharing(0) {
 *   val maxValue = it.maxBy { v -> v.value }.value
 *   maxValue.yielding { "Something different" }
 * }
 * result // result: kotlin.String
 * ```
 * In the example above, the function [share] wil return the string initialised as in [sendButReturn].
 *
 * ### Invalid use:
 *
 * Do not write code after calling the sending or returning values, they must be written at last inside the lambda.
 * ```
 * share(0) {
 *  val maxValue = it.maxBy { v -> v.value }.value
 *  maxValue.yielding { "Don't do this" }
 *  maxValue
 * }
 * ```
 */
suspend fun <T, R> AggregateContext.sharing(
    initial: T,
    transform: SharingContext<T, R>.(StateFlow<Field<T>>) -> StateFlow<SharingResult<T, R>>,
): StateFlow<R> {
    val context = SharingContext<T, R>()
    val result: MutableStateFlow<Option<SharingResult<T, R>>> = MutableStateFlow(none())
    exchange(initial) { flow ->
        combineStates(flow, transform(context, flow)) { field, sharingResult ->
            field.mapWithId{ _, _ -> sharingResult.also { r -> result.update { r.some() } }.toSend }
        }
    }
    return mapStates(result){ it.getOrElse { error("This error should never be thrown") }.toReturn }
}

/**
 * [share] captures the space-time nature of field computation through observation of neighbours' values, starting
 * from an [initial] value, it reduces to a single local value given a [transform] function and updating and sharing to
 * neighbours of a local variable.
 * ```
 * val result = share(0) {
 *   it.maxBy { v -> v.value }.value
 * }
 * result // result: kotlin.Int
 * ```
 * In the example above, the function [share] wil return a value that is the max found in the field.
 **/
suspend fun <T> AggregateContext.share(initial: T, transform: (StateFlow<Field<T>>) -> StateFlow<T>): StateFlow<T> =
    sharing(initial) {
        val result = transform(it)
        mapStates(result) { value -> SharingResult(value, value) }
    }
