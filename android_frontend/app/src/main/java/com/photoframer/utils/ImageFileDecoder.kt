package com.photoframer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * 解码磁盘图片并按 EXIF 朝向转正。
 */
object ImageFileDecoder {
    fun decodeBitmapRespectingExif(
        file: File,
        options: BitmapFactory.Options? = null
    ): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return applyExifOrientation(bitmap, readExifOrientation(file))
    }

    fun decodeThumbnailRespectingExif(
        file: File,
        maxDimension: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (largestDimension / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return decodeBitmapRespectingExif(file, decodeOptions)
    }

    fun requiresOrientationNormalization(file: File): Boolean {
        return when (readExifOrientation(file)) {
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_UNDEFINED -> false
            else -> true
        }
    }

    private fun readExifOrientation(file: File): Int {
        return try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_UNDEFINED
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
            else -> return bitmap
        }

        return try {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            ).also { corrected ->
                if (corrected !== bitmap) {
                    bitmap.recycle()
                }
            }
        } catch (_: Exception) {
            bitmap
        }
    }
}
