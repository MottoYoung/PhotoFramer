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
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
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
