package it.unibo.collektive.samples

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.field.min
import it.unibo.collektive.field.plus
import it.unibo.collektive.reactive.flow.extensions.mapStates
import it.unibo.collektive.reactive.rShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class NodeType {
    SOURCE,
    OBSTACLE,
    DEFAULT,
}

val environment = Environment.manhattanGrid(5, 5)

val sensors = (0..<environment.devicesNumber).map {
    when {
        it == 0 -> MutableStateFlow(NodeType.SOURCE)
        it % 4 == 0 -> MutableStateFlow(NodeType.OBSTACLE)
        else -> MutableStateFlow(NodeType.DEFAULT)
    }
}

fun Aggregate<Int>.gradient(sourceFlow: StateFlow<Boolean>): StateFlow<Double> =
    rShare(Double.POSITIVE_INFINITY) { fieldFlow ->
        rMux(
            { sourceFlow },
            { MutableStateFlow(0.0) },
            { fieldFlow.mapStates { it.plus(1.0).min(Double.POSITIVE_INFINITY) } },
        )
    }

/**
 * TODO.
 *
 * @param deviceId
 * @return
 */
fun Aggregate<Int>.gradientWithObstacles(nodeTypeFlow: StateFlow<NodeType>): StateFlow<Double> =
    rBranch(
        { nodeTypeFlow.mapStates { it == NodeType.OBSTACLE } },
        { MutableStateFlow(-1.0) },
        { gradient(nodeTypeFlow.mapStates { it == NodeType.SOURCE }) },
    )
