# 📷 PhotoFramer PC Demo v2

智能辅助拍摄 App 的完整演示方案，采用 Gemini 2.5 Flash Image 模型一次性生成构图参考图片、文字描述及用户指令。

> **注意**: 此方案由于模型特性，响应速度较慢（30+ 秒）。

---

## 📦 项目结构

```
pc_demo_v2/
├── backend/            # FastAPI 后端服务
├── android_frontend/   # Android 客户端 (Jetpack Compose + CameraX)
├── api_tests/          # API 测试脚本
└── readme.md           # 本文档
```

---

## 🚀 快速开始

### 第一步：启动后端服务

```bash
# 1. 进入后端目录
cd backend

# 2. 安装依赖
pip install -r requirements.txt

# 3. 配置 API 密钥（可选，已有默认值）
export GEMINI_API_KEY="你的API密钥"

# 4. 启动服务
python main.py
# 或者使用 uvicorn：uvicorn main:app --host 0.0.0.0 --port 8000
```

服务启动后访问：
- **Swagger 文档**: http://localhost:8000/docs
- **健康检查**: http://localhost:8000/api/v1/composition/health

### 第二步：配置并运行 Android 客户端

1. **修改后端 API 地址**

   编辑 `android_frontend/app/src/main/java/com/photoframer/data/api/ApiConfig.kt`：
   ```kotlin
   private const val HOST = "192.168.x.x"  // 修改为你电脑的局域网 IP
   ```

   获取 Mac 局域网 IP：
   ```bash
   ifconfig | grep "inet " | grep -v 127.0.0.1
   ```

2. **在 Android Studio 中运行**
   - 打开 `android_frontend` 目录
   - 等待 Gradle 同步
   - 连接手机或启动模拟器
   - 点击 Run 运行

---

## 📱 使用流程

```
相机预览 → 点击分析 → AI分析中(流光动画) → 选择构图方案 → 步骤引导 → 拍照
```

1. **拍摄预览** - 打开 App，相机预览自动启动
2. **一键分析** - 点击分析按钮，上传当前画面
3. **选择方案** - AI 返回 1-4 个优化构图方案
4. **步骤引导** - 根据 Shift/Zoom/View-change 指令调整相机
5. **完成拍摄** - 达成目标构图后拍照

---

## 🔧 API 接口

### POST `/api/v1/composition/analyze`

上传图片并获取构图分析结果。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| image | File | ✅ | 原始图片 (JPEG/PNG) |
| prompt | string | ❌ | 自定义提示词 |
| aspect_ratio | string | ❌ | 生成图片宽高比 |

**curl 测试示例**：
```bash
curl -X POST "http://localhost:8000/api/v1/composition/analyze" \
  -F "image=@/path/to/image.jpg"
```

---

## 🛠️ 技术栈

| 模块 | 技术 |
|------|------|
| **后端** | FastAPI + Uvicorn + Gemini 2.5 Flash Image |
| **前端** | Jetpack Compose + Material3 + CameraX |
| **网络** | Retrofit + OkHttp |
| **架构** | MVVM + StateFlow |

---

## 📁 详细文档

- [后端 README](./backend/README.md)
- [Android 前端 README](./android_frontend/README.md)
