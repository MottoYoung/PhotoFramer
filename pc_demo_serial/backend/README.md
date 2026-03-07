# 📷 PhotoFramer Backend

辅助拍摄 App 的后端服务，基于 FastAPI + Gemini AI 构建。

## 功能特性

- 🎨 **智能构图分析**：上传图片，获取 1-4 个优化构图方案
- 📝 **操作指令生成**：Shift / Zoom / View-change 步骤指导
- 🖼️ **参考图像生成**：AI 生成的目标构图图片（Base64 编码）

## 快速开始

### 1. 安装依赖

```bash
cd pc_demo_v2/backend
pip install -r requirements.txt
```

### 2. 配置 API 密钥

设置环境变量（推荐）：
```bash
export GEMINI_API_KEY="你的API密钥"
```

或直接修改 `config.py` 中的 `GEMINI_API_KEY`。

### 3. 启动服务

```bash
# 方式一：使用 uvicorn 命令
uvicorn main:app --host 0.0.0.0 --port 8000

# 方式二：直接运行（开发模式，支持热重载）
python main.py
```

### 4. 访问文档

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## API 接口

### POST `/api/v1/composition/analyze`

上传图片并获取构图分析结果。

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| image | File | ✅ | 原始图片文件 (JPEG/PNG) |
| prompt | string | ❌ | 自定义提示词，留空使用默认提示词 |
| aspect_ratio | string | ❌ | 生成图片宽高比，如 `16:9` |

**响应示例**：
```json
{
  "success": true,
  "header": {
    "type": "header",
    "total_count": 2,
    "analysis": "原图分析..."
  },
  "compositions": [
    {
      "id": 1,
      "aesthetic_desc": "通过平移和推近...",
      "steps": [
        {
          "step_order": 1,
          "action_type": "Shift",
          "direction": "Left",
          "guide_text": "向左平移相机..."
        }
      ],
      "image_base64": "data:image/png;base64,..."
    }
  ]
}
```

### GET `/api/v1/composition/health`

健康检查接口。

## 使用 curl 测试

```bash
# 健康检查
curl http://localhost:8000/api/v1/composition/health

# 分析图片（使用默认提示词）
curl -X POST "http://localhost:8000/api/v1/composition/analyze" \
  -F "image=@/path/to/your/image.jpg"

# 分析图片（使用自定义提示词）
curl -X POST "http://localhost:8000/api/v1/composition/analyze" \
  -F "image=@/path/to/your/image.jpg" \
  -F "prompt=请分析这张照片的构图"
```

## 项目结构

```
backend/
├── main.py                 # FastAPI 应用入口
├── config.py               # 配置管理
├── requirements.txt        # 依赖清单
├── README.md               # 本文档
├── routers/
│   ├── __init__.py
│   └── composition.py      # 构图分析路由
├── services/
│   ├── __init__.py
│   └── gemini_service.py   # Gemini API 封装
└── schemas/
    ├── __init__.py
    └── composition.py      # Pydantic 数据模型
```

## 技术栈

- **Web 框架**: FastAPI
- **ASGI 服务器**: Uvicorn
- **AI 模型**: Gemini 2.5 Flash Image
- **图片处理**: Pillow
