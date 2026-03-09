# 提示词测评报告 - 纯文本模态

> 生成时间: 2026-03-09 10:51:49
> 测试时间: 20260309_105145
> **模型**: `gemini-2.5-flash`
> 输入图像: `/Users/mottoyoung/学习/毕设/PhotoFramer/test_images/shift/origin/217386_x4_16x9_hltw_box-213-199-981-655_mos1.14_orig.jpg`

---

## 📊 响应时间排行榜

| 排名 | 测试组合 | 平均响应(ms) | 最快(ms) | 最慢(ms) | 标准差(ms) | 成功率 |
|------|----------|-------------|---------|---------|-----------|--------|
| 1 | `v_unified_default` | 0 | 0 | 0 | 0 | 0% |

---

## 🏆 推荐组合

**最佳响应时间**: `v_unified_default`
- 提示词: `v_unified`
- 模型配置: `default`
- 平均响应时间: **0ms**

---

## 📈 按提示词分组分析


---

## 📈 按模型配置分组分析


---

## 📎 附录：提示词配置详情

### `v_unified`
**描述**: 统一格式版本，输出与并行版相同的 JSON Schema（纯文本）

**System Instruction**:
```
# Role: AI Photography Director
# Goal: Analyze the user's input image and provide composition guidance (TEXT ONLY, NO IMAGE GENERATION).

# Action Definitions:
* `Shift`: Moving camera (Left, Right, Up, Down).
* `Zoom`: Adjusting focal length/distance (In, Out).
* `View-change`: Changing angle (High-angle, Low-angle, Side-view).

# JSON Schema (Strict):
You must return a JSON object with this exact structure for EACH composition:
{
  "is_applicable": true,
  "technique": "string",
  "composition_data": {
    "aesthetic_desc_and_resoning": "string",
    "steps": [
      {
        "step_order": 1,
        "action_type": "string",
        "direction": "string",
        "guide_text": "string"
      }
    ]
  }
}

# Constraints:
1. Language: All text must be in Chinese (中文).
2. Output 1-5 compositions, each as a separate JSON object.
3. NO IMAGE GENERATION: Only output JSON text.

```

**User Prompt**: `请分析这张照片，针对不同构图技术（如三分法、中心构图、引导线、前景框架、对角线）提供构图改进方案。
每种方案请输出一个 JSON 对象（包含 is_applicable, technique, composition_data）。
`

---
