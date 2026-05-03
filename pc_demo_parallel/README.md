# PhotoFramer Backend v3.1 (并行化版本)

基于 asyncio 并行化的 Gemini 构图分析后端服务。

## 核心特性

- 🚀 **并行请求**: 5 种构图方案同时发送，响应时间接近单个请求
- 📸 **智能分析**: 自动判断每种构图技术是否适用于当前场景
- 🎯 **操作指令**: 生成 Shift / Zoom / View-change 步骤引导用户

## 快速开始

```bash
# 安装依赖
pip install -r requirements.txt

# 启动服务
python main.py
```

服务将在 http://localhost:8000 启动，API 文档: http://localhost:8000/docs

## API 接口

### POST /composition_analyze

上传图片，并行分析 5 种构图技术，返回适用的方案。

**请求**: `multipart/form-data`
- `image`: 图片文件 (JPEG/PNG)

**响应**:
```json
{
  "success": true,
  "total_techniques": 5,
  "applicable_count": 3,
  "total_time_ms": 8500.0,
  "compositions": [
    {
      "technique": "rule_of_thirds",
      "technique_name": "三分构图",
      "aesthetic_desc": "将人物置于右侧三分线，增强画面平衡",
      "steps": [...],
      "image_base64": "data:image/png;base64,..."
    }
  ]
}
```

### GET /health

健康检查接口。

## 技术架构

```
并行化机制:
┌─────────────────────────────────────────────────────────┐
│  请求图片                                                │
│     │                                                   │
│     ▼                                                   │
│  asyncio.gather() ──┬── 三分构图 ──┐                    │
│                     ├── 中心构图 ──┼── 聚合结果 → 返回  │
│                     ├── 引导线 ────┤                    │
│                     ├── 前景框架 ──┤                    │
│                     └── 对角线 ────┘                    │
└─────────────────────────────────────────────────────────┘
```
