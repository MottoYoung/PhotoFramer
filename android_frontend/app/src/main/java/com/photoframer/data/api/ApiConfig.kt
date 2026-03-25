package com.photoframer.data.api

/**
 * API 配置
 * ⚠️ 请将 BASE_URL 修改为你电脑的局域网 IP 地址
 * 
 * 查看 Mac 局域网 IP: 系统偏好设置 -> 网络 -> Wi-Fi -> IP 地址
 * 或者在终端运行: ifconfig | grep "inet " | grep -v 127.0.0.1
 */
object ApiConfig {
    // TODO: 修改为你的电脑局域网 IP
    private const val HOST = "10.81.209.92"  // ⬅️ 修改这里
    private const val PORT = "8000"
    
    const val BASE_URL = "http://$HOST:$PORT/"
    
    // API 超时设置（秒）
    const val CONNECT_TIMEOUT = 10L
    const val READ_TIMEOUT = 120L  // 图片生成需要较长时间
    const val WRITE_TIMEOUT = 60L
}
