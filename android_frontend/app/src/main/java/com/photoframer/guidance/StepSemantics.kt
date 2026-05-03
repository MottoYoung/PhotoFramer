package com.photoframer.guidance

import com.photoframer.data.api.CompositionStep

private val VIEWPOINT_ACTIONS = setOf(
    "view-change",
    "orbit",
    "raisecamera",
    "lowercamera",
    "step"
)

private val SHIFT_ACTIONS = setOf(
    "shift",
    "level"
)

private val LEVEL_ACTIONS = setOf(
    "level"
)

fun String.normalizedActionType(): String {
    val compact = trim().lowercase().replace("_", "").replace("-", "")
    return when (compact) {
        "viewchange" -> "view-change"
        "orbit" -> "orbit"
        "raisecamera" -> "raisecamera"
        "lowercamera" -> "lowercamera"
        "step" -> "step"
        "shift" -> "shift"
        "level" -> "level"
        "zoom" -> "zoom"
        else -> trim().lowercase()
    }
}

fun String.normalizedDirection(): String = trim().lowercase().replace("_", "-")

fun String.canonicalViewDirection(): String = when (normalizedDirection()) {
    "left", "side-view-left" -> "left"
    "right", "side-view-right" -> "right"
    "up", "high-angle" -> "up"
    "down", "low-angle" -> "down"
    "forward" -> "forward"
    "backward" -> "backward"
    else -> normalizedDirection()
}

fun String.isViewpointActionType(): Boolean = normalizedActionType() in VIEWPOINT_ACTIONS

fun String.isShiftActionType(): Boolean = normalizedActionType() in SHIFT_ACTIONS

fun String.isLevelActionType(): Boolean = normalizedActionType() in LEVEL_ACTIONS

fun CompositionStep.isViewpointAction(): Boolean = actionType.isViewpointActionType()

fun CompositionStep.isShiftAction(): Boolean = actionType.isShiftActionType()

fun CompositionStep.isLevelAction(): Boolean = actionType.isLevelActionType()

fun CompositionStep.isZoomAction(): Boolean = actionType.normalizedActionType() == "zoom"
