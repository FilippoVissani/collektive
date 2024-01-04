package it.unibo.collektive.aggregate.ops

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.some
import it.unibo.collektive.aggregate.AggregateContext
import it.unibo.collektive.field.Field
import it.unibo.collektive.proactive.networking.OutboundMessage
import it.unibo.collektive.reactive.flow.extensions.mapStates
import kotlinx.coroutines.flow.StateFlow

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
fun <R> AggregateContext.neighbouring(type: R): StateFlow<OutboundMessage> {
    val body: (StateFlow<Field<R>>) -> StateFlow<Field<R>> = { flow ->
        mapStates(flow) { field ->
            field.mapWithId { _, x -> x }
        }
    }
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
/*fun <T, R> AggregateContext.sharing(
    initial: T,
    transform: SharingContext<T, R>.(Field<T>) -> SharingResult<T, R>,
): R {
    val context = SharingContext<T, R>()
    var res: Option<SharingResult<T, R>> = none()
    exchange(initial) {
        it.mapWithId { _, _ -> transform(context, it).also { r -> res = r.some() }.toSend }
    }
    return res.getOrElse { error("This error should never be thrown") }.toReturn
}*/

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
/*fun <Initial> AggregateContext.share(initial: Initial, transform: (Field<Initial>) -> Initial): Initial =
    sharing(initial) {
        val res = transform(it)
        SharingResult(res, res)
    }*/
