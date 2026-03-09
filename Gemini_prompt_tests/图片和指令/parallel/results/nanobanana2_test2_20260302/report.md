# 并行构图测评报告

> 生成时间: 2026-03-02 20:52:03
> 测试时间: 20260302_205001
> **模型**: `gemini-3.1-flash-image-preview`
> 输入图像: `/Users/mottoyoung/学习/毕设/PhotoFramer/test_images/input/person.jpg`

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
| 平均并发耗时 | **39498ms** |
| 最快并发耗时 | 31003ms |
| 最慢并发耗时 | 50794ms |

### 按构图技术统计

| 构图技术 | 平均响应(ms) | 成功率 | 适用次数 |
|----------|-------------|--------|----------|
| `center_composition` | 26923 | 100% | 3/3 |
| `diagonal_composition` | 31289 | 100% | 3/3 |
| `foreground_framing` | 25773 | 100% | 3/3 |
| `leading_lines` | 29210 | 100% | 3/3 |
| `rule_of_thirds` | 27950 | 100% | 3/3 |

---

## 📝 详细结果

### 运行 1

- 时间: 2026-03-02T20:50:01.063959
- 并发耗时: **36696ms**
- 成功: 5/5
- 适用: 5/5

- ✅ **rule_of_thirds**: 27457ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 29684ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 27737ms [适用]
  - 生成图片: 1 张
- ✅ **foreground_framing**: 23899ms [适用]
  - 生成图片: 1 张
- ✅ **diagonal_composition**: 36683ms [适用]
  - 生成图片: 1 张

### 运行 2

- 时间: 2026-03-02T20:50:39.766499
- 并发耗时: **50794ms**
- 成功: 5/5
- 适用: 5/5

- ✅ **rule_of_thirds**: 34748ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 29494ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 50782ms [适用]
  - 生成图片: 2 张
- ✅ **foreground_framing**: 24891ms [适用]
  - 生成图片: 1 张
- ✅ **diagonal_composition**: 26204ms [适用]
  - 生成图片: 1 张

### 运行 3

- 时间: 2026-03-02T20:51:32.566745
- 并发耗时: **31003ms**
- 成功: 5/5
- 适用: 5/5

- ✅ **rule_of_thirds**: 21644ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 21589ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 9113ms [适用]
- ✅ **foreground_framing**: 28529ms [适用]
  - 生成图片: 1 张
- ✅ **diagonal_composition**: 30980ms [适用]
  - 生成图片: 1 张

---

## 🎯 推荐构图

以下构图技术在测试中被判定为**适用**:
- ✓ `rule_of_thirds`
- ✓ `center_composition`
- ✓ `leading_lines`
- ✓ `foreground_framing`
- ✓ `diagonal_composition`