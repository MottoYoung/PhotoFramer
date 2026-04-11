package com.photoframer.data.repository

import com.photoframer.data.api.AnalysisResponse
import com.photoframer.data.api.ApiConfig
import com.photoframer.data.api.InFrameCompositionResponse
import com.photoframer.data.api.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * 构图分析仓库
 */
class CompositionRepository {
    
    private val api = RetrofitClient.api
    
    /**
     * 分析图片构图 (v3.1 并行化后端)
     * 
     * @param imageFile 图片文件
     * @return 分析结果
     */
    suspend fun analyzeComposition(
        imageFile: File
    ): Result<AnalysisResponse> {
        return try {
            // 创建图片 Part
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                imageFile.name,
                requestFile
            )
            
            // 调用 API
            val response = api.analyzeComposition(imagePart)
            
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
     * 画面内构图分析
     */
    suspend fun analyzeInFrameComposition(
        imageFile: File
    ): Result<InFrameCompositionResponse> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                imageFile.name,
                requestFile
            )

            val response = api.analyzeInFrameComposition(
                url = ApiConfig.IN_FRAME_COMPOSITION_URL,
                image = imagePart
            )

            if (response.box.size >= 4) {
                Result.success(response)
            } else {
                Result.failure(Exception("画面内构图未返回有效裁切框"))
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
