package com.photoframer.data.repository

import com.photoframer.data.api.AnalysisResponse
import com.photoframer.data.api.ApiConfig
import com.photoframer.data.api.InFrameCompositionResponse
import com.photoframer.data.api.RetrofitClient
import com.photoframer.data.api.StreamCandidateReadyEvent
import com.photoframer.data.api.StreamStartedEvent
import com.photoframer.data.api.StreamSummaryEvent
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody.Companion.FORM
import java.io.IOException
import java.io.File
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val httpClient = RetrofitClient.httpClient
    private val gson = Gson()
    private val analysisCache = linkedMapOf<String, CachedAnalysisResponse>()

    data class StreamAnalysisUpdate(
        val selectedTechniques: List<String>? = null,
        val completedCount: Int? = null,
        val totalCandidateCount: Int? = null,
        val partialResponse: AnalysisResponse? = null,
        val finalResponse: AnalysisResponse? = null
    )
    
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

    suspend fun analyzeCompositionStream(
        imageFile: File,
        onUpdate: suspend (StreamAnalysisUpdate) -> Unit
    ): Result<AnalysisResponse> {
        val cacheKey = buildImageCacheKey(imageFile)
        getCachedAnalysis(cacheKey)?.let { cached ->
            onUpdate(StreamAnalysisUpdate(finalResponse = cached))
            return Result.success(cached)
        }

        return try {
            val response = withContext(Dispatchers.IO) {
                executeStreamRequest(imageFile, onUpdate)
            }
            if (response.success && response.compositions.isNotEmpty()) {
                cacheAnalysis(cacheKey, response)
            }
            Result.success(response)
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

    private suspend fun executeStreamRequest(
        imageFile: File,
        onUpdate: suspend (StreamAnalysisUpdate) -> Unit
    ): AnalysisResponse {
        val multipartBody = MultipartBody.Builder()
            .setType(FORM)
            .addFormDataPart(
                "image",
                imageFile.name,
                imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("${ApiConfig.AI_COMPOSITION_URL}composition_analyze_stream")
            .post(multipartBody)
            .addHeader("Accept", "text/event-stream")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(retrofit2.Response.error<String>(
                    response.code,
                    response.body?.string().orEmpty().toResponseBody("text/plain".toMediaTypeOrNull())
                ))
            }

            val body = response.body ?: throw IOException("服务未返回响应体")
            val reader = body.charStream().buffered()
            val compositions = mutableListOf<com.photoframer.data.api.CompositionResult>()
            var selectedTechniques: List<String> = emptyList()
            var totalTechniques = 0
            var completedCount = 0
            var applicableCount = 0
            var totalTimeMs = 0f

            fun buildPartialResponse(): AnalysisResponse {
                return AnalysisResponse(
                    success = true,
                    message = null,
                    totalTechniques = when {
                        totalTechniques > 0 -> totalTechniques
                        selectedTechniques.isNotEmpty() -> selectedTechniques.size
                        else -> selectedTechniques.size
                    },
                    applicableCount = applicableCount.coerceAtLeast(compositions.size),
                    totalTimeMs = totalTimeMs,
                    compositions = compositions.toList()
                )
            }

            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isBlank()) continue
                if (payload == "[DONE]") break

                val eventType = gson.fromJson(payload, Map::class.java)["event"] as? String ?: continue
                when (eventType) {
                    "analysis_started" -> {
                        val event = gson.fromJson(payload, StreamStartedEvent::class.java)
                        selectedTechniques = event.selectedTechniques
                        totalTechniques = when {
                            event.requestedTechniques.isNotEmpty() -> event.requestedTechniques.size
                            else -> 0
                        }
                        onUpdate(
                            StreamAnalysisUpdate(
                                selectedTechniques = selectedTechniques,
                                completedCount = completedCount,
                                totalCandidateCount = selectedTechniques.size,
                                partialResponse = buildPartialResponse()
                            )
                        )
                    }
                    "candidate_ready" -> {
                        val event = gson.fromJson(payload, StreamCandidateReadyEvent::class.java)
                        completedCount = event.completedCount
                        applicableCount = event.applicableCount
                        compositions.removeAll { it.technique == event.composition.technique }
                        compositions += event.composition
                        onUpdate(
                            StreamAnalysisUpdate(
                                completedCount = completedCount,
                                totalCandidateCount = if (totalTechniques > 0) totalTechniques else selectedTechniques.size,
                                partialResponse = buildPartialResponse()
                            )
                        )
                    }
                    "technique_skipped", "candidate_duplicate_skipped" -> {
                        val rawMap = gson.fromJson(payload, Map::class.java)
                        completedCount = (rawMap["completed_count"] as? Number)?.toInt() ?: completedCount
                        onUpdate(
                            StreamAnalysisUpdate(
                                completedCount = completedCount,
                                totalCandidateCount = if (totalTechniques > 0) totalTechniques else selectedTechniques.size,
                                partialResponse = buildPartialResponse()
                            )
                        )
                    }
                    "summary" -> {
                        val event = gson.fromJson(payload, StreamSummaryEvent::class.java)
                        totalTechniques = when {
                            event.totalTechniques > 0 -> event.totalTechniques
                            else -> totalTechniques
                        }
                        applicableCount = event.applicableCount
                        totalTimeMs = event.totalTimeMs
                        val finalResponse = buildPartialResponse()
                        onUpdate(
                            StreamAnalysisUpdate(
                                completedCount = completedCount,
                                totalCandidateCount = totalTechniques,
                                finalResponse = finalResponse
                            )
                        )
                        return finalResponse
                    }
                }
            }

            val fallbackResponse = buildPartialResponse()
            if (fallbackResponse.compositions.isEmpty()) {
                throw IOException("未收到可用构图方案")
            }
            onUpdate(
                StreamAnalysisUpdate(
                    completedCount = completedCount,
                    totalCandidateCount = if (totalTechniques > 0) totalTechniques else selectedTechniques.size,
                    finalResponse = fallbackResponse
                )
            )
            return fallbackResponse
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
