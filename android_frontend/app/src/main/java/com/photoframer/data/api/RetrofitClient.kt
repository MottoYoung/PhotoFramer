package com.photoframer.data.api

import com.photoframer.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端单例
 */
object RetrofitClient {
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.AI_COMPOSITION_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val api: PhotoFramerApi = retrofit.create(PhotoFramerApi::class.java)
}
