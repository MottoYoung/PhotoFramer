# PhotoFramer Android 前端

辅助拍摄 App 的 Android 客户端，基于 Jetpack Compose + CameraX 构建。

## 功能特性

- 📷 **相机预览**：全屏 CameraX 预览
- 🎨 **智能分析**：一键分析构图，获取 AI 优化建议
- 📝 **引导指令**：步骤式构图引导
- ✨ **流光动画**：分析时的渐变边框动画效果

## 快速开始

### 1. 修改后端 API 地址

打开 `app/src/main/java/com/photoframer/data/api/ApiConfig.kt`，确认两阶段后端地址：

```kotlin
// const val AI_COMPOSITION_URL = "http://[mylocalhost]:[port]/"
const val AI_COMPOSITION_URL = "http://aicrop.312237.xyz/"
```

当前默认指向部署好的公网两阶段后端；如果你要切本地测试，直接把上面的本地地址那行取消注释并替换端口即可。

### 2. 启动后端服务

如果你要本地调试后端，可在电脑上运行：
```bash
cd ../pc_demo_parallel_two_stage_new
uvicorn main:app --host 0.0.0.0 --port 8000
```

### 3. 在 Android Studio 中打开项目

1. 打开 Android Studio
2. 选择 `File -> Open`
3. 选择 `android_frontend` 目录
4.点击sync project with gradle files 等待同步完成
5. 连接手机或启动模拟器
6. 点击 Run 运行

## 项目结构

```
app/src/main/java/com/photoframer/
├── MainActivity.kt              # 入口 Activity
├── data/
│   ├── api/
│   │   ├── ApiConfig.kt         # API 配置
│   │   ├── ApiModels.kt         # 数据模型
│   │   ├── PhotoFramerApi.kt    # Retrofit 接口
│   │   └── RetrofitClient.kt    # HTTP 客户端
│   └── repository/
│       └── CompositionRepository.kt
├── viewmodel/
│   └── CameraViewModel.kt       # 状态管理
└── ui/
    ├── theme/                   # 主题配置
    ├── state/                   # UI 状态定义
    ├── components/              # UI 组件
    │   ├── Buttons.kt
    │   ├── LoadingOverlay.kt
    │   ├── CompositionCard.kt
    │   └── GuidancePanel.kt
    └── screens/
        └── CameraScreen.kt      # 主相机界面
```

## 技术栈

- **UI**: Jetpack Compose + Material3
- **相机**: CameraX
- **网络**: Retrofit + OkHttp
- **图片**: Coil
- **架构**: MVVM + StateFlow

## UI 流程

```
相机预览 → 点击分析 → 分析中(流光动画) → 选择方案 → 步骤引导 → 拍照
```
