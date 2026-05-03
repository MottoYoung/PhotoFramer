package com.photoframer.data.api

/**
 * API 配置
 * AI 构图服务默认走云端地址；本地调试时可切换到下面注释的本地地址模板。
 */
object ApiConfig {
//     const val AI_COMPOSITION_URL = "http://10.165.85.49:8000/"
    const val AI_COMPOSITION_URL = "http://aicrop.312237.xyz/"
    const val IN_FRAME_COMPOSITION_URL = "https://crop2.312237.xyz/predict?return_preview=0"
    
    // API 超时设置（秒）
    const val CONNECT_TIMEOUT = 10L
    const val READ_TIMEOUT = 120L  // 图片生成需要较长时间
    const val WRITE_TIMEOUT = 60L
}
