package it.unibo.collektive.samples

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.operators.share
import it.unibo.collektive.field.min
import it.unibo.collektive.field.plus

enum class NodeType {
    SOURCE,
    OBSTACLE,
    DEFAULT,
}

val environment = Environment.manhattanGrid(3, 3)

fun Aggregate<Int>.gradient(nodeType: NodeType): Double =
    share(Double.POSITIVE_INFINITY) { field ->
        when (nodeType) {
            NodeType.SOURCE -> 0.0
            NodeType.OBSTACLE -> Double.POSITIVE_INFINITY
            NodeType.DEFAULT -> (field + 1.0).min(Double.POSITIVE_INFINITY)
        }
    }

fun Aggregate<Int>.aggregateProgram(deviceId: Int) = gradient(
    when {
        deviceId == 0 -> NodeType.SOURCE
        deviceId % 2 == 0 -> NodeType.OBSTACLE
        else -> NodeType.DEFAULT
    },
)
