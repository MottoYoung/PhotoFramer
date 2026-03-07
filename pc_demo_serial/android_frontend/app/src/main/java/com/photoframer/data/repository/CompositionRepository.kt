package com.photoframer.data.repository

import com.photoframer.data.api.AnalysisResponse
import com.photoframer.data.api.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 构图分析仓库
 */
class CompositionRepository {
    
    private val api = RetrofitClient.api
    
    /**
     * 分析图片构图
     * 
     * @param imageFile 图片文件
     * @param prompt 可选的自定义提示词
     * @return 分析结果
     */
    suspend fun analyzeComposition(
        imageFile: File,
        prompt: String? = null
    ): Result<AnalysisResponse> {
        return try {
            // 创建图片 Part
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                imageFile.name,
                requestFile
            )
            
            // 创建 prompt Part（如果有）
            val promptPart = prompt?.toRequestBody("text/plain".toMediaTypeOrNull())
            
            // 调用 API
            val response = api.analyzeComposition(imagePart, promptPart)
            
            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(Exception(response.message ?: "分析失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 健康检查
     */
    suspend fun healthCheck(): Result<Boolean> {
        return try {
            val response = api.healthCheck()
            Result.success(response.status == "healthy")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
