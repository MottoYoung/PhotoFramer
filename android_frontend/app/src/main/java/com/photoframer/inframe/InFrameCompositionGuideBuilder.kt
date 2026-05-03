package com.photoframer.inframe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Base64
import com.photoframer.data.api.CompositionResult
import com.photoframer.data.api.CompositionStep
import com.photoframer.data.api.InFrameCropCandidate
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
    private const val TECHNIQUE_ID_PREFIX = "in_frame_crop"
    private const val CENTER_SHIFT_THRESHOLD = 0.035f
    private const val MIN_ZOOM_STEP = 1.08f

    fun build(
        sourceBitmap: Bitmap,
        response: InFrameCompositionResponse
    ): InFrameCompositionGuide? = buildAll(sourceBitmap, response).firstOrNull()

    fun buildAll(
        sourceBitmap: Bitmap,
        response: InFrameCompositionResponse
    ): List<InFrameCompositionGuide> {
        val crops = extractCandidates(response)
        if (crops.isEmpty()) return emptyList()

        return crops.mapIndexedNotNull { index, crop ->
            buildGuide(
                sourceBitmap = sourceBitmap,
                response = response,
                crop = crop,
                index = index,
                totalCount = crops.size
            )
        }
    }

    private fun buildGuide(
        sourceBitmap: Bitmap,
        response: InFrameCompositionResponse,
        crop: InFrameCropCandidate,
        index: Int,
        totalCount: Int
    ): InFrameCompositionGuide? {
        val cropRect = toCropRect(
            box = crop.box,
            bitmapWidth = sourceBitmap.width,
            bitmapHeight = sourceBitmap.height
        ) ?: return null

        val fallbackPreviewBase64 = if (totalCount == 1) response.croppedPreviewJpegBase64 else null
        val previewBitmap = decodePreviewBitmap(crop.croppedPreviewJpegBase64 ?: fallbackPreviewBase64)
            ?: Bitmap.createBitmap(
                sourceBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )

        // 大多数手机数码变焦本质上是中心裁切并等比放大。
        // 这里取 width/height 裁切倍率的较大者，确保目标裁切框能被完整覆盖，
        // 避免预览里看起来“主体还没裁进来”。
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
            technique = "${TECHNIQUE_ID_PREFIX}_${index + 1}",
            techniqueName = buildTechniqueName(crop, index, totalCount),
            aestheticDesc = buildAestheticDescription(
                sceneType = response.sceneType,
                cropRect = cropRect,
                sourceBitmap = sourceBitmap,
                crop = crop
            ),
            steps = steps,
            imageBase64 = null,
            isRecommended = (crop.rank ?: (index + 1)) == 1
        )

        return InFrameCompositionGuide(
            composition = composition,
            previewBitmap = previewBitmap,
            validationBitmap = sourceBitmap,
            validationConfig = validationConfig
        )
    }

    private fun extractCandidates(response: InFrameCompositionResponse): List<InFrameCropCandidate> {
        val multiCropCandidates = response.crops
            .filter { it.box.size >= 4 }
            .sortedWith(
                compareBy<InFrameCropCandidate> { it.rank ?: Int.MAX_VALUE }
                    .thenByDescending { it.score ?: Float.NEGATIVE_INFINITY }
            )
        if (multiCropCandidates.isNotEmpty()) return multiCropCandidates

        return if (response.box.size >= 4) {
            listOf(
                InFrameCropCandidate(
                    rank = 1,
                    score = response.score,
                    box = response.box,
                    center = response.center,
                    cropSize = response.cropSize,
                    normalizedBox = response.normalizedBox,
                    normalizedCenter = response.normalizedCenter,
                    croppedPreviewJpegBase64 = response.croppedPreviewJpegBase64
                )
            )
        } else {
            emptyList()
        }
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
        sourceBitmap: Bitmap,
        crop: InFrameCropCandidate
    ): String {
        val cropAreaRatio = (
            cropRect.width().toFloat() * cropRect.height().toFloat()
            ) / (sourceBitmap.width.toFloat() * sourceBitmap.height.toFloat())

        val areaText = when {
            cropAreaRatio < 0.35f -> "大幅收紧画面"
            cropAreaRatio < 0.6f -> "适度收紧取景"
            else -> "轻微收紧边缘"
        }

        val subjectText = when (sceneType?.lowercase()) {
            "portrait", "person" -> "突出人物主体"
            "landscape" -> "保留画面重心"
            else -> "贴近推荐裁切"
        }
        val scoreText = crop.score?.let { "，匹配度 ${(it * 100).toInt()}%" }.orEmpty()

        return "$areaText，$subjectText$scoreText"
    }

    private fun buildTechniqueName(
        crop: InFrameCropCandidate,
        index: Int,
        totalCount: Int
    ): String {
        if (totalCount <= 1) return "画面内构图"
        val displayRank = crop.rank ?: (index + 1)
        return "参考构图 $displayRank"
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
