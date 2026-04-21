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

fun String.normalizedActionType(): String = trim().lowercase()

fun String.isViewpointActionType(): Boolean = normalizedActionType() in VIEWPOINT_ACTIONS

fun String.isShiftActionType(): Boolean = normalizedActionType() in SHIFT_ACTIONS

fun CompositionStep.isViewpointAction(): Boolean = actionType.isViewpointActionType()

fun CompositionStep.isShiftAction(): Boolean = actionType.isShiftActionType()

fun CompositionStep.isZoomAction(): Boolean = actionType.normalizedActionType() == "zoom"
