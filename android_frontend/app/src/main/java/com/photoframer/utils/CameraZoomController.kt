package com.photoframer.utils

import androidx.camera.core.CameraInfo
import kotlin.math.abs

data class CameraZoomPreset(
    val lensIndex: Int,
    val label: String,
    val effectiveZoomRatio: Float
)

object CameraZoomController {
    private const val SNAP_TOLERANCE_RATIO = 0.12f
    private const val MIN_SNAP_DISTANCE = 0.08f

    fun buildPresets(indexedLenses: List<Pair<Int, CameraLensOption>>): List<CameraZoomPreset> {
        return indexedLenses
            .map { (index, lens) ->
                CameraZoomPreset(
                    lensIndex = index,
                    label = lens.displayName,
                    effectiveZoomRatio = lens.targetZoomRatio ?: 1f
                )
            }
            .sortedBy { it.effectiveZoomRatio }
    }

    fun effectiveZoomRatio(lens: CameraLensOption?, rawZoomRatio: Float): Float {
        return (lens?.targetZoomRatio ?: 1f) * rawZoomRatio
    }

    fun chooseLensForZoom(
        presets: List<CameraZoomPreset>,
        targetEffectiveZoom: Float
    ): CameraZoomPreset? {
        if (presets.isEmpty()) return null

        return presets
            .filter { it.effectiveZoomRatio <= targetEffectiveZoom + 0.001f }
            .maxByOrNull { it.effectiveZoomRatio }
            ?: presets.first()
    }

    fun computeRawZoomRatio(
        lens: CameraLensOption?,
        targetEffectiveZoom: Float,
        cameraInfo: CameraInfo?
    ): Float {
        val baseZoom = lens?.targetZoomRatio ?: 1f
        val targetRawZoom = (targetEffectiveZoom / baseZoom).coerceAtLeast(1f)
        val zoomState = cameraInfo?.zoomState?.value
        val minZoom = zoomState?.minZoomRatio ?: 1f
        val maxZoom = zoomState?.maxZoomRatio ?: 8f
        return targetRawZoom.coerceIn(minZoom, maxZoom)
    }

    fun snapZoomRatio(
        targetEffectiveZoom: Float,
        presets: List<CameraZoomPreset>
    ): Float {
        val nearest = presets.minByOrNull { abs(it.effectiveZoomRatio - targetEffectiveZoom) }
            ?: return targetEffectiveZoom
        val distance = abs(nearest.effectiveZoomRatio - targetEffectiveZoom)
        val tolerance = maxOf(nearest.effectiveZoomRatio * SNAP_TOLERANCE_RATIO, MIN_SNAP_DISTANCE)
        return if (distance <= tolerance) {
            nearest.effectiveZoomRatio
        } else {
            targetEffectiveZoom
        }
    }

    fun formatZoomRatio(zoomRatio: Float): String {
        val rounded = (zoomRatio * 10f).toInt() / 10f
        val normalized = if (abs(rounded - rounded.toInt()) < 0.05f) {
            rounded.toInt().toString()
        } else {
            String.format("%.1f", rounded)
        }
        return "${normalized}x"
    }

    fun findDefaultLensIndex(
        indexedLenses: List<Pair<Int, CameraLensOption>>
    ): Int? {
        return indexedLenses.minByOrNull { (_, lens) ->
            abs((lens.targetZoomRatio ?: 1f) - 1f)
        }?.first
    }
}
