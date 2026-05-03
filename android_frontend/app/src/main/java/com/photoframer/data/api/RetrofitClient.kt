package com.photoframer.data.api

import com.photoframer.BuildConfig
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端单例
 */
object RetrofitClient {
    private const val MAX_RETRY_COUNT = 1
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var attempt = 0
        var lastException: IOException? = null

        while (attempt <= MAX_RETRY_COUNT) {
            try {
                val response = chain.proceed(request)
                if (!response.isSuccessful && response.code in 500..599 && attempt < MAX_RETRY_COUNT) {
                    response.close()
                    attempt += 1
                    continue
                }
                return@Interceptor response
            } catch (error: IOException) {
                lastException = error
                if (attempt >= MAX_RETRY_COUNT) {
                    throw error
                }
            }
            attempt += 1
        }

        throw lastException ?: IOException("请求失败且未返回响应")
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(retryInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.AI_COMPOSITION_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val api: PhotoFramerApi = retrofit.create(PhotoFramerApi::class.java)
}
