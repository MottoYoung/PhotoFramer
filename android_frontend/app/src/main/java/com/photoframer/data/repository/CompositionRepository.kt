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
import java.net.SocketTimeoutException
import java.security.MessageDigest
import retrofit2.HttpException

/**
 * 构图分析仓库
 */
class CompositionRepository {
    private data class CachedAnalysisResponse(
        val response: AnalysisResponse,
        val cachedAtMs: Long
    )

    companion object {
        private const val ANALYSIS_CACHE_TTL_MS = 5 * 60 * 1000L
    }
    
    private val api = RetrofitClient.api
    private val analysisCache = linkedMapOf<String, CachedAnalysisResponse>()
    
    /**
     * 分析图片构图 (v3.1 并行化后端)
     * 
     * @param imageFile 图片文件
     * @return 分析结果
     */
    suspend fun analyzeComposition(
        imageFile: File
    ): Result<AnalysisResponse> {
        val cacheKey = buildImageCacheKey(imageFile)
        getCachedAnalysis(cacheKey)?.let { cached ->
            return Result.success(cached)
        }

        return try {
            val response = executeWithSingleRetry {
                val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData(
                    "image",
                    imageFile.name,
                    requestFile
                )
                api.analyzeComposition(imagePart)
            }

            if (response.success) {
                if (response.compositions.isNotEmpty()) {
                    cacheAnalysis(cacheKey, response)
                }
                Result.success(response)
            } else {
                Result.failure(Exception(response.message ?: "分析失败"))
            }
        } catch (e: HttpException) {
            Result.failure(Exception(mapAiHttpError(e)))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("分析超时，请重试"))
        } catch (e: IOException) {
            Result.failure(Exception("网络连接失败，已自动重试一次，请检查网络后再试"))
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

    private suspend fun <T> executeWithSingleRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: IOException) {
            block()
        }
    }

    private fun getCachedAnalysis(cacheKey: String): AnalysisResponse? {
        pruneExpiredCache()
        val cached = analysisCache[cacheKey] ?: return null
        if (System.currentTimeMillis() - cached.cachedAtMs > ANALYSIS_CACHE_TTL_MS) {
            analysisCache.remove(cacheKey)
            return null
        }
        return cached.response
    }

    private fun cacheAnalysis(cacheKey: String, response: AnalysisResponse) {
        pruneExpiredCache()
        analysisCache[cacheKey] = CachedAnalysisResponse(
            response = response,
            cachedAtMs = System.currentTimeMillis()
        )
    }

    private fun pruneExpiredCache() {
        val now = System.currentTimeMillis()
        val iterator = analysisCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.cachedAtMs > ANALYSIS_CACHE_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun buildImageCacheKey(imageFile: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        imageFile.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun mapAiHttpError(error: HttpException): String {
        return when (error.code()) {
            408, 504 -> "分析超时，请重试"
            413 -> "图片太大，已压缩后仍无法处理"
            429 -> "分析服务当前较忙，请稍后再试"
            500, 502, 503 -> "分析服务暂时不可用，请稍后重试"
            else -> "构图分析失败（HTTP ${error.code()}）"
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
