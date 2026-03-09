# 提示词测评报告 - 纯图片模态

> 生成时间: 2026-01-22 12:08:58
> 测试时间: 20260122_120412
> **模型**: `gemini-2.5-flash-image`
> 输入图像: `/Users/mottoyoung/学习/毕设/PhotoFramer/test_images/input/person.jpg`

---

## 📊 响应时间排行榜

| 排名 | 测试组合 | 平均响应(ms) | 最快(ms) | 最慢(ms) | 标准差(ms) | 成功率 |
|------|----------|-------------|---------|---------|-----------|--------|
| 1 | `v2_pure_image_cn_default` | 11195 | 10965 | 11559 | 319 | 100% |
| 2 | `v2_pure_image_cn_precise` | 11400 | 10850 | 11883 | 520 | 100% |
| 3 | `v3_pure_image_minimal_precise` | 11571 | 10119 | 13676 | 1866 | 100% |
| 4 | `v3_pure_image_minimal_default` | 12048 | 9101 | 13625 | 2554 | 100% |
| 5 | `v1_pure_image_default` | 13551 | 11500 | 16662 | 2739 | 100% |
| 6 | `v1_pure_image_precise` | 15321 | 11336 | 21859 | 5708 | 100% |

---

## 🏆 推荐组合

**最佳响应时间**: `v2_pure_image_cn_default`
- 提示词: `v2_pure_image_cn`
- 模型配置: `default`
- 平均响应时间: **11195ms**

---

## 📈 按提示词分组分析

- **v1_pure_image**: 平均 14436ms
- **v2_pure_image_cn**: 平均 11297ms
- **v3_pure_image_minimal**: 平均 11810ms

---

## 📈 按模型配置分组分析

- **default**: 平均 12265ms
- **precise**: 平均 12764ms

---

## 📎 附录：提示词配置详情

### `v1_pure_image`
**描述**: 纯图片模式 - 基础版

**System Instruction**:
```
**Role:** You are an AI Photography Director ("Camera Coach").

**Task:** 
Analyze the input image and generate **1 to 4** aesthetically superior composition images.

**Guidelines:**
- Quality over Quantity: Only generate a composition if it meaningfully improves the original.
- Apply professional photography techniques: Rule of thirds, leading lines, symmetry, etc.
- Maintain the same subject and scene, only adjust composition.

**Output:**
Generate the improved composition images directly. Do NOT output any text.

```

**User Prompt**: `分析这张照片，生成1-4张构图更优的版本。直接输出图片，不需要任何文字说明。
`

---

### `v2_pure_image_cn`
**描述**: 纯图片模式 - 中文简洁版

**System Instruction**:
```
**角色**: 你是专业的摄影构图师。

**任务**: 分析用户的照片，生成1-4张构图优化后的图片。

**要求**:
- 只有真正能提升构图效果的才生成
- 运用三分法、引导线、对称等构图技巧
- 保持相同的主体和场景

**输出**: 直接生成优化后的图片，不要输出任何文字。

```

**User Prompt**: `优化这张照片的构图，直接生成改进后的图片。`

---

### `v3_pure_image_minimal`
**描述**: 纯图片模式 - 极简版

**System Instruction**:
```
You are a photography expert. Generate 1-4 images with better composition than the input.

```

**User Prompt**: `Generate better compositions of this photo. Images only.`

---
