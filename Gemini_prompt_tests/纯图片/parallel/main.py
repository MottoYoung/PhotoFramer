"""
并行提示词测评主入口 - 纯图输出版本

输出模态：仅返回构图目标图片，不返回文本说明
    # 并发同步测试（5种构图并发执行）
    python main.py sync -i test_image.jpg
    
    # 批量测试（Batch API）
    python main.py batch -i test_image.jpg
    
    # 生成报告
    python main.py report
    
    # 预览配置
    python main.py config
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from prompts_config import (
    load_prompts_from_yaml, load_model_configs_from_yaml,
    generate_composition_requests
)
from sync_runner import run_parallel_assessment
from batch_runner import run_batch_assessment
from report_generator import generate_report, find_latest_result_dir


def cmd_sync(args):
    """执行并发同步测试"""
    print("\n" + "=" * 60)
    print("🚀 并发同步测试 - 5 种构图方案并行（纯图输出）")
    print("=" * 60)
    
    if args.prompt:
        print(f"   指定提示词版本: {args.prompt}")
    
    results = run_parallel_assessment(
        input_image_path=args.image,
        repeats=args.repeats,
        delay_between_runs=args.delay,
        prompt_id=args.prompt
    )
    
    # 自动生成报告
    result_dir = find_latest_result_dir()
    if result_dir:
        generate_report(result_dir)


def cmd_batch(args):
    """执行批量测试"""
    print("\n" + "=" * 60)
    print("🎯 批量测试 - Batch API（纯图输出）")
    print("=" * 60)
    
    if args.prompt:
        print(f"   指定提示词版本: {args.prompt}")
    
    job_info = run_batch_assessment(
        input_image_path=args.image,
        wait=not args.no_wait,
        prompt_id=args.prompt
    )
    
    if args.no_wait:
        print(f"\n作业已提交，作业名称: {job_info.job_name}")


def cmd_report(args):
    """生成测评报告"""
    if args.input:
        result_dir = Path(args.input)
    else:
        result_dir = find_latest_result_dir()
        if not result_dir:
            print("❌ 未找到结果目录，请先运行测试")
            return
    
    report = generate_report(result_dir, args.output)
    
    if not args.quiet:
        print("\n" + "=" * 60)
        print(report)


def cmd_config(args):
    """预览配置"""
    print("\n" + "=" * 60)
    print("📋 并行提示词配置预览")
    print("=" * 60)
    
    prompt_sets = load_prompts_from_yaml()
    model_configs = load_model_configs_from_yaml()
    
    print(f"\n📝 提示词集合 ({len(prompt_sets)}):")
    for ps in prompt_sets:
        print(f"  - {ps.id}: {ps.description}")
        print(f"    包含 {len(ps.user_prompts)} 种构图技术:")
        for tech_id in ps.user_prompts.keys():
            print(f"      • {tech_id}")
    
    print(f"\n⚙️ 模型配置 ({len(model_configs)}):")
    for c in model_configs:
        print(f"  - {c.id}: temp={c.temperature}, topK={c.top_k}, topP={c.top_p}")
    
    print(f"\n🎯 生成请求示例:")
    if prompt_sets:
        requests = generate_composition_requests()
        for req in requests:
            print(f"  - {req.request_id}")


def main():
    parser = argparse.ArgumentParser(
        description="并行提示词测评工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 并发同步测试（5种构图并行）
  python main.py sync -i test_image.jpg -r 3
  
  # 批量测试（Batch API，成本50%）
  python main.py batch -i test_image.jpg
  
  # 生成报告
  python main.py report
  
  # 预览配置
  python main.py config
        """
    )
    
    subparsers = parser.add_subparsers(dest="command", help="子命令")
    
    # sync 命令
    sync_parser = subparsers.add_parser("sync", help="并发同步测试")
    sync_parser.add_argument("-i", "--image", required=True, help="输入图像路径")
    sync_parser.add_argument("-p", "--prompt", help="指定提示词版本ID（默认全部）")
    sync_parser.add_argument("-r", "--repeats", type=int, default=1, help="重复运行次数")
    sync_parser.add_argument("-d", "--delay", type=float, default=2.0, help="运行间隔（秒）")
    sync_parser.set_defaults(func=cmd_sync)
    
    # batch 命令
    batch_parser = subparsers.add_parser("batch", help="批量测试（Batch API）")
    batch_parser.add_argument("-i", "--image", required=True, help="输入图像路径")
    batch_parser.add_argument("-p", "--prompt", help="指定提示词版本ID（默认全部）")
    batch_parser.add_argument("--no-wait", action="store_true", help="不等待作业完成")
    batch_parser.set_defaults(func=cmd_batch)
    
    # report 命令
    report_parser = subparsers.add_parser("report", help="生成测评报告")
    report_parser.add_argument("-i", "--input", help="结果目录（默认最新）")
    report_parser.add_argument("-o", "--output", help="输出报告路径")
    report_parser.add_argument("-q", "--quiet", action="store_true", help="不打印报告内容")
    report_parser.set_defaults(func=cmd_report)
    
    # config 命令
    config_parser = subparsers.add_parser("config", help="预览配置")
    config_parser.set_defaults(func=cmd_config)
    
    args = parser.parse_args()
    
    if args.command is None:
        parser.print_help()
        return
    
    args.func(args)


if __name__ == "__main__":
    main()
