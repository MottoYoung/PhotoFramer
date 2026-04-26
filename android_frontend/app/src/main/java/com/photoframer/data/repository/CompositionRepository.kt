package com.photoframer.data.repository

import com.photoframer.data.api.AnalysisResponse
import com.photoframer.data.api.ApiConfig
import com.photoframer.data.api.InFrameCompositionResponse
import com.photoframer.data.api.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.io.File
import retrofit2.HttpException

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

            Result.success(response)
        } catch (e: HttpException) {
            Result.failure(Exception(mapInFrameHttpError(e)))
        } catch (e: IOException) {
            Result.failure(Exception("网络连接失败，请确认当前设备可以访问画面内分析服务"))
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

    private fun mapInFrameHttpError(error: HttpException): String {
        return when (error.code()) {
            404 -> "画面内分析接口不存在，请检查服务地址配置"
            413 -> "图片太大，画面内分析服务拒绝处理"
            429 -> "画面内分析服务当前过于繁忙，请稍后再试"
            500, 502, 503, 504, 530 -> "画面内分析服务暂时不可用，请稍后重试"
            else -> "画面内分析请求失败（HTTP ${error.code()}）"
        }
    }
}
