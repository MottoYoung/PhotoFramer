package com.photoframer.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * 图像转换工具
 * 将 CameraX ImageProxy 转换为 Bitmap
 */
object ImageConverter {
    
    /**
     * 将 ImageProxy 转换为 Bitmap
     * 注意：这是一个相对耗时的操作，应在后台线程执行
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            when (imageProxy.format) {
                ImageFormat.YUV_420_888 -> yuvToBitmap(imageProxy)
                ImageFormat.JPEG -> jpegToBitmap(imageProxy)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * YUV_420_888 格式转换
     */
    private fun yuvToBitmap(imageProxy: ImageProxy): Bitmap? {
        val width = imageProxy.width
        val height = imageProxy.height
        val nv21 = yuv420888ToNv21(imageProxy)
        
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            width,
            height,
            null
        )
        
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            80,  // 质量 80%
            out
        )
        
        val imageBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        // 旋转图片以匹配显示方向
        return rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        copyLumaPlane(
            plane = yPlane,
            width = width,
            height = height,
            out = nv21
        )

        interleaveChromaPlanes(
            uPlane = uPlane,
            vPlane = vPlane,
            width = width,
            height = height,
            out = nv21,
            outOffset = ySize
        )

        return nv21
    }

    private fun copyLumaPlane(
        plane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        out: ByteArray
    ) {
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var outputIndex = 0

        for (row in 0 until height) {
            val rowOffset = row * rowStride
            for (col in 0 until width) {
                out[outputIndex++] = buffer.get(rowOffset + col * pixelStride)
            }
        }
    }

    private fun interleaveChromaPlanes(
        uPlane: ImageProxy.PlaneProxy,
        vPlane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        out: ByteArray,
        outOffset: Int
    ) {
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        var outputIndex = outOffset

        for (row in 0 until chromaHeight) {
            val uRowOffset = row * uPlane.rowStride
            val vRowOffset = row * vPlane.rowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowOffset + col * uPlane.pixelStride
                val vIndex = vRowOffset + col * vPlane.pixelStride
                out[outputIndex++] = vBuffer.get(vIndex)
                out[outputIndex++] = uBuffer.get(uIndex)
            }
        }
    }
    
    /**
     * JPEG 格式转换
     */
    private fun jpegToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
    }
    
    /**
     * 旋转 Bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap?, rotationDegrees: Int): Bitmap? {
        if (bitmap == null || rotationDegrees == 0) return bitmap
        
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    /**
     * 将 Bitmap 缩放到指定宽度，保持长宽比不变。
     * 引导验证使用固定宽度坐标系，便于阈值和 UI 叠加层保持一致。
     */
    fun scaleBitmapToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (targetWidth <= 0 || bitmap.width == targetWidth) {
            return bitmap
        }

        val scale = targetWidth.toFloat() / bitmap.width
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, true)
    }

    /**
     * 将 Bitmap 按目标长宽比做中心裁切。
     * 引导阶段需要让“实时分析帧”尽量贴近用户当前看到的取景窗口。
     */
    fun centerCropToAspectRatio(bitmap: Bitmap, targetRatio: Float): Bitmap {
        if (targetRatio <= 0f) {
            return bitmap
        }

        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        if (srcWidth <= 0 || srcHeight <= 0) {
            return bitmap
        }

        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        if (kotlin.math.abs(srcRatio - targetRatio) < 0.01f) {
            return bitmap
        }

        val (cropWidth, cropHeight) = if (srcRatio > targetRatio) {
            ((srcHeight * targetRatio).toInt().coerceAtLeast(1)) to srcHeight
        } else {
            srcWidth to ((srcWidth / targetRatio).toInt().coerceAtLeast(1))
        }

        val x = ((srcWidth - cropWidth) / 2).coerceAtLeast(0)
        val y = ((srcHeight - cropHeight) / 2).coerceAtLeast(0)

        return try {
            Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
        } catch (_: Exception) {
            bitmap
        }
    }
    
    /**
     * 缩放 Bitmap（降低分辨率以加快处理速度）
     */
    fun scaleBitmap(bitmap: Bitmap, maxDimension: Int = 640): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scale = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
