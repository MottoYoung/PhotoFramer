# 并行构图测评报告

> 生成时间: 2026-03-02 20:49:09
> 测试时间: 20260302_204706
> **模型**: `gemini-3.1-flash-image-preview`
> 输入图像: `/Users/mottoyoung/学习/毕设/PhotoFramer/test_images/input/input.jpg`

---

## 📊 测评概览

- **构图技术数**: 5
- **运行次数**: 3

**测试的构图技术**:
- `rule_of_thirds`
- `center_composition`
- `leading_lines`
- `foreground_framing`
- `diagonal_composition`

---

## ⏱️ 并发性能分析

| 指标 | 值 |
|------|-----|
| 平均并发耗时 | **39804ms** |
| 最快并发耗时 | 29720ms |
| 最慢并发耗时 | 50890ms |

### 按构图技术统计

| 构图技术 | 平均响应(ms) | 成功率 | 适用次数 |
|----------|-------------|--------|----------|
| `center_composition` | 29730 | 100% | 3/3 |
| `diagonal_composition` | 30464 | 100% | 3/3 |
| `foreground_framing` | 37087 | 100% | 3/3 |
| `leading_lines` | 25657 | 100% | 3/3 |
| `rule_of_thirds` | 26133 | 100% | 3/3 |

---

## 📝 详细结果

### 运行 1

- 时间: 2026-03-02T20:47:06.338329
- 并发耗时: **50890ms**
- 成功: 5/5
- 适用: 5/5

- ✅ **rule_of_thirds**: 26268ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 29570ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 32218ms [适用]
  - 生成图片: 1 张
- ✅ **foreground_framing**: 50859ms [适用]
  - 生成图片: 2 张
- ✅ **diagonal_composition**: 24058ms [适用]
  - 生成图片: 1 张

### 运行 2

- 时间: 2026-03-02T20:47:59.234821
- 并发耗时: **29720ms**
- 成功: 5/5
- 适用: 5/5

- ✅ **rule_of_thirds**: 17803ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 26192ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 21633ms [适用]
  - 生成图片: 1 张
- ✅ **foreground_framing**: 29697ms [适用]
  - 生成图片: 1 张
- ✅ **diagonal_composition**: 28557ms [适用]
  - 生成图片: 1 张

### 运行 3

- 时间: 2026-03-02T20:48:30.963579
- 并发耗时: **38802ms**
- 成功: 5/5
- 适用: 5/5

- ✅ **rule_of_thirds**: 34328ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 33428ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 23119ms [适用]
  - 生成图片: 1 张
- ✅ **foreground_framing**: 30705ms [适用]
  - 生成图片: 1 张
- ✅ **diagonal_composition**: 38776ms [适用]
  - 生成图片: 1 张

---

## 🎯 推荐构图

以下构图技术在测试中被判定为**适用**:
- ✓ `rule_of_thirds`
- ✓ `center_composition`
- ✓ `leading_lines`
- ✓ `foreground_framing`
- ✓ `diagonal_composition`