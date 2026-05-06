package com.photoframer.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 低开销感知哈希，用于补足 AI 参考图与实拍帧之间的全局结构相似度。
 */
object PerceptualHashMatcher {
    private const val HASH_SIZE = 32
    private const val LOW_FREQUENCY_SIZE = 8

    fun computeHash(bitmap: Bitmap): Long {
        val rgba = Mat()
        val gray = Mat()
        val resized = Mat()
        val floatMat = Mat()
        val dct = Mat()

        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(
                gray,
                resized,
                Size(HASH_SIZE.toDouble(), HASH_SIZE.toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )
            resized.convertTo(floatMat, CvType.CV_32F)
            Core.dct(floatMat, dct)

            val values = ArrayList<Double>(LOW_FREQUENCY_SIZE * LOW_FREQUENCY_SIZE)
            for (row in 0 until LOW_FREQUENCY_SIZE) {
                for (col in 0 until LOW_FREQUENCY_SIZE) {
                    values += dct.get(row, col)[0]
                }
            }

            val median = values.sorted()[values.size / 2]
            var hash = 0L
            values.forEachIndexed { index, value ->
                if (value >= median) {
                    hash = hash or (1L shl index)
                }
            }
            hash
        } catch (_: Exception) {
            0L
        } finally {
            rgba.release()
            gray.release()
            resized.release()
            floatMat.release()
            dct.release()
        }
    }

    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    fun similarity(hash1: Long, hash2: Long): Float {
        if (hash1 == 0L || hash2 == 0L) return 0f
        val distance = hammingDistance(hash1, hash2)
        return (1f - distance / 64f).coerceIn(0f, 1f)
    }
}
