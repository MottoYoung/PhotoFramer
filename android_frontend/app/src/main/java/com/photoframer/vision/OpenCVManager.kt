package com.photoframer.vision

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * OpenCV 初始化管理器
 */
object OpenCVManager {
    
    private const val TAG = "OpenCVManager"
    private var isInitialized = false
    
    /**
     * 初始化 OpenCV
     * 应在 Application 或 MainActivity 中调用
     */
    fun init(context: Context): Boolean {
        if (isInitialized) {
            return true
        }
        
        return try {
            if (OpenCVLoader.initLocal()) {
                Log.d(TAG, "OpenCV 初始化成功")
                isInitialized = true
                true
            } else {
                Log.e(TAG, "OpenCV 初始化失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV 初始化异常", e)
            false
        }
    }
    
    /**
     * 检查 OpenCV 是否已初始化
     */
    fun isReady(): Boolean = isInitialized
}
