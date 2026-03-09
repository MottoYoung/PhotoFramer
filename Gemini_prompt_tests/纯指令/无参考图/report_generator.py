"""
测评报告生成器 - 纯文本模态版本

从测试结果生成 Markdown 格式的对比分析报告
"""

import json
from pathlib import Path
from datetime import datetime
from typing import Any


BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"


def generate_report(
    result_dir: str | Path,
    output_path: str | Path | None = None
) -> str:
    """
    根据测试结果生成 Markdown 报告
    
    Args:
        result_dir: 结果目录（包含 summary.json）
        output_path: 输出报告路径
        
    Returns:
        报告内容
    """
    result_dir = Path(result_dir)
    summary_path = result_dir / "summary.json"
    
    if not summary_path.exists():
        raise FileNotFoundError(f"未找到 summary.json: {summary_path}")
    
    with open(summary_path, "r", encoding="utf-8") as f:
        summary = json.load(f)
    
    # 判断是 batch 还是 sync 结果
    is_sync = "repeats_per_case" in summary
    
    if is_sync:
        report = _generate_sync_report(summary, result_dir)
    else:
        report = _generate_batch_report(summary, result_dir)
    
    # 保存报告
    if output_path is None:
        output_path = result_dir / "report.md"
    
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(report)
    
    print(f"✅ 报告已生成: {output_path}")
    return report


def _generate_sync_report(summary: dict, result_dir: Path) -> str:
    """生成同步测评报告（阶段2）"""
    results = summary.get("results", [])
    timestamp = summary.get("timestamp", "unknown")
    model_name = summary.get("model_name", "N/A")
    prompts_config = summary.get("prompts_config", {})
    
    lines = [
        "# 提示词测评报告 - 纯文本模态",
        "",
        f"> 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"> 测试时间: {timestamp}",
        f"> **模型**: `{model_name}`",
        f"> 输入图像: `{summary.get('input_image', 'N/A')}`",
        "",
        "---",
        "",
        "## 📊 响应时间排行榜",
        "",
        "| 排名 | 测试组合 | 平均响应(ms) | 最快(ms) | 最慢(ms) | 标准差(ms) | 成功率 |",
        "|------|----------|-------------|---------|---------|-----------|--------|",
    ]
    
    # 按平均响应时间排序
    sorted_results = sorted(results, key=lambda r: r.get("avg_response_time_ms", float("inf")))
    
    for i, r in enumerate(sorted_results):
        success_rate = r["successful_runs"] / r["total_runs"] * 100 if r["total_runs"] > 0 else 0
        lines.append(
            f"| {i+1} | `{r['case_id']}` | "
            f"{r['avg_response_time_ms']:.0f} | "
            f"{r['min_response_time_ms']:.0f} | "
            f"{r['max_response_time_ms']:.0f} | "
            f"{r['std_response_time_ms']:.0f} | "
            f"{success_rate:.0f}% |"
        )
    
    lines.extend([
        "",
        "---",
        "",
        "## 🏆 推荐组合",
        "",
    ])
    
    if sorted_results:
        best = sorted_results[0]
        lines.extend([
            f"**最佳响应时间**: `{best['case_id']}`",
            f"- 提示词: `{best['prompt_id']}`",
            f"- 模型配置: `{best['model_config_id']}`",
            f"- 平均响应时间: **{best['avg_response_time_ms']:.0f}ms**",
            "",
        ])
    
    # 按提示词分组分析
    lines.extend([
        "---",
        "",
        "## 📈 按提示词分组分析",
        "",
    ])
    
    prompt_groups: dict[str, list] = {}
    for r in results:
        pid = r.get("prompt_id", "unknown")
        if pid not in prompt_groups:
            prompt_groups[pid] = []
        prompt_groups[pid].append(r)
    
    for prompt_id, group in prompt_groups.items():
        avg_times = [r["avg_response_time_ms"] for r in group if r["successful_runs"] > 0]
        if avg_times:
            group_avg = sum(avg_times) / len(avg_times)
            lines.append(f"- **{prompt_id}**: 平均 {group_avg:.0f}ms")
    
    lines.extend([
        "",
        "---",
        "",
        "## 📈 按模型配置分组分析",
        "",
    ])
    
    config_groups: dict[str, list] = {}
    for r in results:
        cid = r.get("model_config_id", "unknown")
        if cid not in config_groups:
            config_groups[cid] = []
        config_groups[cid].append(r)
    
    for config_id, group in config_groups.items():
        avg_times = [r["avg_response_time_ms"] for r in group if r["successful_runs"] > 0]
        if avg_times:
            group_avg = sum(avg_times) / len(avg_times)
            lines.append(f"- **{config_id}**: 平均 {group_avg:.0f}ms")
    
    # 附录：提示词完整内容
    if prompts_config:
        lines.extend([
            "",
            "---",
            "",
            "## 📎 附录：提示词配置详情",
            "",
        ])
        for prompt_id, config in prompts_config.items():
            lines.extend([
                f"### `{prompt_id}`",
                f"**描述**: {config.get('description', 'N/A')}",
                "",
                "**System Instruction**:",
                "```",
                config.get('system_instruction', 'N/A'),
                "```",
                "",
                f"**User Prompt**: `{config.get('user_prompt', 'N/A')}`",
                "",
                "---",
                "",
            ])
    
    return "\n".join(lines)


def _generate_batch_report(summary: dict, result_dir: Path) -> str:
    """生成批量测评报告（阶段1）"""
    results = summary.get("results", [])
    
    lines = [
        "# 提示词测评报告 - 纯文本模态 (质量筛选)",
        "",
        f"> 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"> 作业名称: `{summary.get('display_name', 'N/A')}`",
        f"> 状态: {summary.get('status', 'N/A')}",
        "",
        "---",
        "",
        "## 📊 测试结果概览",
        "",
        f"- 总测试数: {summary.get('total_cases', 0)}",
        f"- 成功: {summary.get('successful_cases', 0)}",
        f"- 失败: {summary.get('failed_cases', 0)}",
        "",
        "---",
        "",
        "## 📝 各用例输出预览",
        "",
    ]
    
    for r in results:
        case_id = r.get("case_id", "unknown")
        success = r.get("success", False)
        status = "✅" if success else "❌"
        
        lines.append(f"### {status} {case_id}")
        lines.append("")
        
        if success:
            texts = r.get("texts", [])
            if texts:
                # 只显示第一段文本的预览
                preview = texts[0][:500] + "..." if len(texts[0]) > 500 else texts[0]
                lines.append("**输出预览:**")
                lines.append("```")
                lines.append(preview)
                lines.append("```")
            
            images = r.get("images", [])
            if images:
                lines.append(f"**生成图片数**: {len(images)}")
        else:
            error = r.get("error", "Unknown error")
            lines.append(f"**错误**: {error}")
        
        lines.append("")
    
    lines.extend([
        "---",
        "",
        "## 🎯 人工评审建议",
        "",
        "请查看各用例的输出，标记质量评分后，选择优质组合进入阶段2（响应时间测试）。",
        "",
        "| 用例ID | 质量评分(1-5) | 备注 |",
        "|--------|--------------|------|",
    ])
    
    for r in results:
        if r.get("success"):
            lines.append(f"| `{r['case_id']}` | ___ | |")
    
    return "\n".join(lines)


def find_latest_result_dir() -> Path | None:
    """查找最新的结果目录"""
    if not RESULTS_DIR.exists():
        return None
    
    dirs = [d for d in RESULTS_DIR.iterdir() if d.is_dir() and (d / "summary.json").exists()]
    if not dirs:
        return None
    
    return max(dirs, key=lambda d: d.stat().st_mtime)


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="生成测评报告")
    parser.add_argument("--input", "-i", help="结果目录路径（默认使用最新）")
    parser.add_argument("--output", "-o", help="输出报告路径")
    
    args = parser.parse_args()
    
    if args.input:
        result_dir = Path(args.input)
    else:
        result_dir = find_latest_result_dir()
        if not result_dir:
            print("❌ 未找到结果目录，请先运行测试")
            exit(1)
        print(f"📂 使用最新结果目录: {result_dir}")
    
    report = generate_report(result_dir, args.output)
    print("\n" + "="*60)
    print(report)
