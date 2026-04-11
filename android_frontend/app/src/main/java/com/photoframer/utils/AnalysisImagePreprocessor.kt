package com.photoframer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class PreparedAnalysisImage(
    val file: File,
    val bitmap: Bitmap? = null,
    val isTemporary: Boolean = false
) {
    fun cleanup() {
        if (isTemporary) {
            file.delete()
        }
    }
}

/**
 * 将拍照结果压缩到更适合网络分析的尺寸，减少上传和服务端处理时间。
 */
object AnalysisImagePreprocessor {
    private const val MAX_DIMENSION = 1920
    private const val JPEG_QUALITY = 85
    private const val MAX_UPLOAD_FILE_BYTES = 1_800_000L

    fun prepare(
        sourceFile: File,
        requireBitmap: Boolean = false
    ): PreparedAnalysisImage {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)

        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw IllegalArgumentException("无法读取图片尺寸")
        }

        val sourceMaxDimension = max(sourceWidth, sourceHeight)
        val needsResize = sourceMaxDimension > MAX_DIMENSION
        val needsRecompress = needsResize || sourceFile.length() > MAX_UPLOAD_FILE_BYTES

        if (!needsRecompress && !requireBitmap) {
            return PreparedAnalysisImage(file = sourceFile)
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(sourceWidth, sourceHeight, MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: throw IllegalArgumentException("无法解码图片")
        val scaledBitmap = scaleBitmapIfNeeded(decodedBitmap, MAX_DIMENSION)

        if (scaledBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        if (!needsRecompress) {
            return if (requireBitmap) {
                PreparedAnalysisImage(file = sourceFile, bitmap = scaledBitmap)
            } else {
                scaledBitmap.recycle()
                PreparedAnalysisImage(file = sourceFile)
            }
        }

        val tempFile = File(
            sourceFile.parentFile ?: sourceFile.absoluteFile.parentFile,
            "analysis_${System.currentTimeMillis()}_${sourceFile.nameWithoutExtension}.jpg"
        )

        FileOutputStream(tempFile).use { output ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }

        return if (requireBitmap) {
            PreparedAnalysisImage(
                file = tempFile,
                bitmap = scaledBitmap,
                isTemporary = true
            )
        } else {
            scaledBitmap.recycle()
            PreparedAnalysisImage(
                file = tempFile,
                isTemporary = true
            )
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int
    ): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height

        while (max(currentWidth, currentHeight) > maxDimension * 2) {
            inSampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }

        return inSampleSize
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val currentMax = max(width, height)

        if (currentMax <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / currentMax.toFloat()
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }
}
