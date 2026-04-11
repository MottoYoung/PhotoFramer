package com.photoframer.inframe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Base64
import com.photoframer.data.api.CompositionResult
import com.photoframer.data.api.CompositionStep
import com.photoframer.data.api.InFrameCompositionResponse
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class InFrameCompositionGuide(
    val composition: CompositionResult,
    val previewBitmap: Bitmap,
    val validationBitmap: Bitmap,
    val validationConfig: InFrameGuideValidationConfig
)

/**
 * 将画面内构图接口响应转为可复用的本地引导方案。
 */
object InFrameCompositionGuideBuilder {
    private const val TECHNIQUE_ID = "in_frame_crop"
    private const val CENTER_SHIFT_THRESHOLD = 0.035f
    private const val MIN_ZOOM_STEP = 1.08f

    fun build(
        sourceBitmap: Bitmap,
        response: InFrameCompositionResponse
    ): InFrameCompositionGuide? {
        val cropRect = toCropRect(
            box = response.box,
            bitmapWidth = sourceBitmap.width,
            bitmapHeight = sourceBitmap.height
        ) ?: return null

        val previewBitmap = decodePreviewBitmap(response.croppedPreviewJpegBase64)
            ?: Bitmap.createBitmap(
                sourceBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )

        val zoomRatio = max(
            sourceBitmap.width.toFloat() / cropRect.width().toFloat(),
            sourceBitmap.height.toFloat() / cropRect.height().toFloat()
        ).coerceIn(1.0f, 8.0f)

        val validationConfig = InFrameGuideValidationConfig(
            targetCenterXNormalized = cropRect.exactCenterX() / sourceBitmap.width.toFloat(),
            targetCenterYNormalized = cropRect.exactCenterY() / sourceBitmap.height.toFloat(),
            targetZoomRatio = zoomRatio
        )

        val steps = buildSteps(
            sourceWidth = sourceBitmap.width,
            sourceHeight = sourceBitmap.height,
            cropRect = cropRect,
            targetZoomRatio = zoomRatio
        )

        val composition = CompositionResult(
            technique = TECHNIQUE_ID,
            techniqueName = "画面内构图",
            aestheticDesc = buildAestheticDescription(response.sceneType, cropRect, sourceBitmap),
            steps = steps,
            imageBase64 = null,
            isRecommended = true
        )

        return InFrameCompositionGuide(
            composition = composition,
            previewBitmap = previewBitmap,
            validationBitmap = sourceBitmap,
            validationConfig = validationConfig
        )
    }

    private fun buildSteps(
        sourceWidth: Int,
        sourceHeight: Int,
        cropRect: Rect,
        targetZoomRatio: Float
    ): List<CompositionStep> {
        val steps = mutableListOf<CompositionStep>()

        val frameCenterX = sourceWidth / 2f
        val frameCenterY = sourceHeight / 2f
        val cropCenterX = cropRect.exactCenterX()
        val cropCenterY = cropRect.exactCenterY()

        val dxNorm = (cropCenterX - frameCenterX) / sourceWidth
        val dyNorm = (cropCenterY - frameCenterY) / sourceHeight
        val centerOffset = sqrt(dxNorm * dxNorm + dyNorm * dyNorm)

        if (centerOffset >= CENTER_SHIFT_THRESHOLD) {
            steps += CompositionStep(
                stepOrder = steps.size + 1,
                actionType = "Shift",
                direction = dominantShiftDirection(dxNorm, dyNorm),
                guideText = buildShiftGuideText(dxNorm, dyNorm)
            )
        }

        if (targetZoomRatio >= MIN_ZOOM_STEP || steps.isEmpty()) {
            steps += CompositionStep(
                stepOrder = steps.size + 1,
                actionType = "Zoom",
                direction = "In",
                guideText = "放大到约 ${"%.1f".format(targetZoomRatio)}x，让画面贴近参考裁切"
            )
        }

        return steps
    }

    private fun dominantShiftDirection(dxNorm: Float, dyNorm: Float): String {
        return if (abs(dxNorm) >= abs(dyNorm)) {
            if (dxNorm >= 0f) "Right" else "Left"
        } else {
            if (dyNorm >= 0f) "Down" else "Up"
        }
    }

    private fun buildShiftGuideText(dxNorm: Float, dyNorm: Float): String {
        val hasHorizontalBias = abs(dxNorm) >= 0.02f
        val hasVerticalBias = abs(dyNorm) >= 0.02f

        return when {
            hasHorizontalBias && hasVerticalBias -> "先把画面中心移到参考图中心"
            hasHorizontalBias && dxNorm > 0f -> "先把取景中心往右移到参考图中心"
            hasHorizontalBias && dxNorm < 0f -> "先把取景中心往左移到参考图中心"
            hasVerticalBias && dyNorm > 0f -> "先把取景中心往下移到参考图中心"
            hasVerticalBias && dyNorm < 0f -> "先把取景中心往上移到参考图中心"
            else -> "先把取景中心对齐参考图"
        }
    }

    private fun buildAestheticDescription(
        sceneType: String?,
        cropRect: Rect,
        sourceBitmap: Bitmap
    ): String {
        val cropAreaRatio = (
            cropRect.width().toFloat() * cropRect.height().toFloat()
            ) / (sourceBitmap.width.toFloat() * sourceBitmap.height.toFloat())

        val areaText = when {
            cropAreaRatio < 0.35f -> "大幅收紧画面"
            cropAreaRatio < 0.6f -> "适度收紧取景"
            else -> "轻微收紧边缘"
        }

        return when (sceneType?.lowercase()) {
            "portrait" -> "$areaText，突出人物主体"
            "landscape" -> "$areaText，保留画面重心"
            else -> "$areaText，贴近推荐裁切"
        }
    }

    /**
     * 接口返回的 box 为 [left, top, right, bottom]，且 right/bottom 为闭区间。
     */
    private fun toCropRect(
        box: List<Int>,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Rect? {
        if (box.size < 4 || bitmapWidth <= 0 || bitmapHeight <= 0) return null

        val left = box[0].coerceIn(0, bitmapWidth - 1)
        val top = box[1].coerceIn(0, bitmapHeight - 1)
        val rightInclusive = box[2].coerceIn(left, bitmapWidth - 1)
        val bottomInclusive = box[3].coerceIn(top, bitmapHeight - 1)

        val rightExclusive = (rightInclusive + 1).coerceAtMost(bitmapWidth)
        val bottomExclusive = (bottomInclusive + 1).coerceAtMost(bitmapHeight)

        if (rightExclusive <= left || bottomExclusive <= top) return null

        return Rect(left, top, rightExclusive, bottomExclusive)
    }

    private fun decodePreviewBitmap(base64String: String?): Bitmap? {
        if (base64String.isNullOrBlank()) return null

        return try {
            val pureBase64 = base64String.substringAfter("base64,", base64String)
            val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
