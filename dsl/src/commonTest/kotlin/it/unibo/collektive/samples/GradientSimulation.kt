package it.unibo.collektive.samples

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.operators.share
import it.unibo.collektive.field.min
import it.unibo.collektive.field.plus
import kotlinx.coroutines.flow.MutableStateFlow

enum class NodeType {
    SOURCE,
    OBSTACLE,
    DEFAULT,
}

val environment = Environment.manhattanGrid(5, 5)

fun getNodeType(id: Int) = when {
    id == 0 -> NodeType.SOURCE
    id % 4 == 0 -> NodeType.OBSTACLE
    else -> NodeType.DEFAULT
}

val reactiveSensors = (0..<environment.devicesNumber).map {
    MutableStateFlow(
        when (it) {
            0 -> NodeType.SOURCE
            2, 7, 12 -> NodeType.OBSTACLE
            else -> NodeType.DEFAULT
        },
    )
}

fun Aggregate<Int>.gradient(source: Boolean): Double =
    share(Double.POSITIVE_INFINITY) { field ->
        when {
            source -> 0.0
            else -> (field + 1.0).min(Double.POSITIVE_INFINITY)
        }
    }

fun Aggregate<Int>.gradientWithObstacles(nodeType: NodeType): Double =
    when {
        nodeType == NodeType.OBSTACLE -> -1.0
        else -> gradient(nodeType == NodeType.SOURCE)
    }
