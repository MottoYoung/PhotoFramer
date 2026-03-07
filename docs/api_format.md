# PhotoFramer 前后端数据格式规范 (v3.1)

## API 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/v1/composition/analyze` | POST | 上传图片，返回构图方案 |
| `/api/v1/composition/health` | GET | 健康检查 |

---

## 请求格式

### POST /api/v1/composition/analyze

**Content-Type**: `multipart/form-data`

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| image | File | ✅ | 图片文件 (JPEG/PNG) |

---

## 响应格式

### AnalysisResponse

```json
{
  "success": true,
  "message": null,
  "total_techniques": 5,
  "applicable_count": 3,
  "total_time_ms": 12583.5,
  "compositions": [
    {
      "technique": "rule_of_thirds",
      "technique_name": "三分构图",
      "aesthetic_desc": "将主体放置在三分线交点...",
      "steps": [...],
      "image_base64": "data:image/png;base64,..."
    }
  ]
}
```

| 字段 | 类型 | 描述 |
|------|------|------|
| `success` | bool | 请求是否成功 |
| `message` | str? | 错误消息（成功时为 null） |
| `total_techniques` | int | 请求的构图技术总数 |
| `applicable_count` | int | 适用的构图方案数量 |
| `total_time_ms` | float | 请求总耗时（毫秒） |
| `compositions` | list | 构图方案列表 |

---

### CompositionResult

```json
{
  "technique": "rule_of_thirds",
  "technique_name": "三分构图",
  "aesthetic_desc": "利用三分法将主体置于画面黄金分割点",
  "steps": [
    {
      "step_order": 1,
      "action_type": "Shift",
      "direction": "Left",
      "guide_text": "向左平移约20厘米"
    }
  ],
  "image_base64": "data:image/png;base64,...",
  "response_time_ms": 6228.5
}
```

| 字段 | 类型 | 描述 |
|------|------|------|
| `technique` | str | 构图技术ID（英文标识） |
| `technique_name` | str | 构图技术中文名称 |
| `aesthetic_desc` | str | 美学描述（中文） |
| `steps` | list | 操作步骤列表 |
| `image_base64` | str? | 生成图片的 Base64 编码（可能为 null） |
| `response_time_ms` | float? | 该方案的响应时间（毫秒） |

---

### CompositionStep

```json
{
  "step_order": 1,
  "action_type": "Shift",
  "direction": "Left",
  "guide_text": "向左平移约20厘米"
}
```

| 字段 | 类型 | 描述 |
|------|------|------|
| `step_order` | int | 步骤序号（从 1 开始） |
| `action_type` | str | 操作类型：`Shift`, `Zoom`, `View-change`, `Rotate-CW` 等 |
| `direction` | str | 操作方向：`Left`, `Right`, `Up`, `Down`, `In`, `Out`, `High-angle` 等 |
| `guide_text` | str | 中文指导文本 |

---

### HealthResponse

```json
{
  "status": "healthy",
  "model": "gemini-2.5-flash-image",
  "techniques": ["rule_of_thirds", "center_composition", ...]
}
```

---

## 支持的构图技术 (v3.1 并行版)

| technique | technique_name | 描述 |
|-----------|---------------|------|
| `rule_of_thirds` | 三分构图 | 将主体放置在三分线交点 |
| `center_composition` | 中心构图 | 主体居中，强调对称性 |
| `leading_lines` | 引导线构图 | 利用线条引导视线 |
| `foreground_framing` | 前景框架 | 利用前景元素框住主体 |
| `diagonal_composition` | 对角线构图 | 沿对角线布置元素 |

---

## 版本差异

| 特性 | v2 串行版 | v3.1 并行版 |
|------|----------|-----------|
| 请求模式 | 串行（单次请求） | 并行（5 个并发请求） |
| 构图技术 | 动态（1-4 个） | 固定（5 种并行分析） |
| 响应时间 | ≈15-30s | ≈12-15s |
| 数据格式 | **统一** | **统一** |
