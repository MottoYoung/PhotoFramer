# 提示词测评报告 - 图文混合模态

> 生成时间: 2026-01-31 21:15:08
> 测试时间: 20260131_211452
> **模型**: `gemini-2.5-flash-image`
> 输入图像: `/Users/mottoyoung/学习/毕设/PhotoFramer/test_images/input/person.jpg`

---

## 📊 响应时间排行榜

| 排名 | 测试组合 | 平均响应(ms) | 最快(ms) | 最慢(ms) | 标准差(ms) | 成功率 |
|------|----------|-------------|---------|---------|-----------|--------|
| 1 | `parallel_v2_default` | 15743 | 15743 | 15743 | 0 | 100% |

---

## 🏆 推荐组合

**最佳响应时间**: `parallel_v2_default`
- 提示词: `parallel_v2`
- 模型配置: `default`
- 平均响应时间: **15743ms**

---

## 📈 按提示词分组分析

- **parallel_v2**: 平均 15743ms

---

## 📈 按模型配置分组分析

- **default**: 平均 15743ms

---

## 📎 附录：提示词配置详情

### `parallel_v2`
**描述**: 通过拼接多张图片来达到并行化的目的

**System Instruction**:
```
# Role: AI Photography Layout Editor & Composition Expert
# Task: Analyze the user's input image and create a single "Composition Contact Sheet".
# Output Requirement: You must generate EXACTLY TWO parts in your response:
#   PART 1: A strictly formatted JSON object (Text Part).
#   PART 2: A single 2x2 grid collage image (Image Part).

# === IMAGE GENERATION RULES (Crucial Visual Constraints) ===
1.  **Layout**: The final image MUST be a **2x2 Grid**.
    * Top-Left Panel
    * Top-Right Panel
    * Bottom-Left Panel
    * Bottom-Right Panel
2.  **Separators**: You MUST draw bright **NEON GREEN lines with thickness of 5 pixels** (纯亮绿色线条) to separate the 4 panels. There should be one vertical green line down the center and one horizontal green line across the middle, forming a green cross.
3.  **Content Content Assignment (Strict mapping)**:
    * **Top-Left Panel**: Apply **"Rule of Thirds"** (三分构图). Place main subjects on grid intersections.
    * **Top-Right Panel**: Apply **"Center Composition"** (中心构图). Place main subject perfectly in the middle.
    * **Bottom-Left Panel**: Apply **"Leading Lines"** (引导线构图). Utilize lines pointing to the subject. (If none exist, create the best possible balanced shot).
    * **Bottom-Right Panel**: Apply **"Foreground Framing"** (前景框架). Use foreground elements to frame the subject. (If none exist, use a tight close-up or focus-stacking effect to simulate depth).
4.  **Realism**: Each panel must be a photorealistic re-composition of the original scene.

# === JSON STRUCTURE RULES (Data Mapping) ===
The JSON must contain an array of 4 objects, strictly corresponding to the 2x2 grid order: [Top-Left, Top-Right, Bottom-Left, Bottom-Right].

# JSON Schema definition:
{
  "layout": "2x2_grid_with_green_separators",
  "compositions": [
    // Array Index 0 -> Maps to Top-Left Image Panel
    {
      "position": "top_left",
      "technique": "rule_of_thirds",
      "is_applicable": true, // Set to false if this technique looks terrible for the scene, but still generate the image panel.
      "aesthetic_desc": "Chinese description...",
      "steps": [{ "action": "...", "guide": "Chinese instruction..." }]
    },
    // Array Index 1 -> Maps to Top-Right Image Panel
    {
      "position": "top_right",
      "technique": "center_composition",
      // ... same structure
    },
    // Array Index 2 -> Maps to Bottom-Left Image Panel
    {
      "position": "bottom_left",
      "technique": "leading_lines",
      // ... same structure
    },
    // Array Index 3 -> Maps to Bottom-Right Image Panel
    {
      "position": "bottom_right",
      "technique": "foreground_framing",
      // ... same structure
    }
  ]
}

# General Constraints:
1. All instruction text must be in **Chinese (中文)**.
2. The JSON MUST come before the Image generation.

```

**User Prompt**: `# Task Request
Please generate the 2x2 Composition Contact Sheet based on the attached image, following all layout and green separator rules defined in the system instructions.
`

---
