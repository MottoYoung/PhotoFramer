"""
纯指令/有参考图 测评主入口

输入：原始照片 + 参考构图图（目标图）
输出：从原图到参考图的相机操作 JSON 指令（纯文本）

使用方法:
    # 同步测试（推荐）
    python main.py sync -i <原图路径> --ref <参考图路径>
    python main.py sync -i origin.jpg --ref edit.jpg -p v1_cot -r 2

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
    PROMPT_VERSIONS, MODEL_CONFIGS,
    generate_test_matrix, load_prompts_from_yaml, load_model_configs_from_yaml
)
from sync_runner import run_sync_assessment
from report_generator import generate_report, find_latest_result_dir


def cmd_sync(args):
    """执行同步测试（原图 + 参考图 → 操作指令）"""
    print("\n" + "=" * 60)
    print("⚡ 同步测试 - 纯指令/有参考图")
    print("=" * 60)

    if args.prompt:
        print(f"   指定提示词版本: {args.prompt}")

    results = run_sync_assessment(
        original_image_path=args.image,
        reference_image_path=args.ref,
        repeats=args.repeats,
        delay_between_calls=args.delay,
        prompt_id=args.prompt,
    )

    # 自动生成报告
    result_dir = find_latest_result_dir()
    if result_dir:
        generate_report(result_dir)


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
    """预览配置和测试矩阵"""
    print("\n" + "=" * 60)
    print("📋 配置预览 - 纯指令/有参考图")
    print("=" * 60)

    prompts = load_prompts_from_yaml()
    configs = load_model_configs_from_yaml()
    cases = list(generate_test_matrix())

    print(f"\n提示词版本 ({len(prompts)}):")
    for p in prompts:
        print(f"  - {p.id}: {p.description}")

    print(f"\n模型配置 ({len(configs)}):")
    for c in configs:
        print(f"  - {c.id}: temp={c.temperature}, topK={c.top_k}, topP={c.top_p}")

    print(f"\n总测试组合数: {len(cases)}")


def main():
    parser = argparse.ArgumentParser(
        description="纯指令/有参考图 测评工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 测试所有提示词版本，每个重复2次
  python main.py sync -i origin.jpg --ref edit.jpg -r 2

  # 只测试 v1_cot 版本
  python main.py sync -i origin.jpg --ref edit.jpg -p v1_cot -r 3

  # 生成报告
  python main.py report

  # 预览配置
  python main.py config
        """
    )

    subparsers = parser.add_subparsers(dest="command", help="子命令")

    # sync 命令
    sync_parser = subparsers.add_parser("sync", help="同步测试（原图+参考图 → 操作指令）")
    sync_parser.add_argument("-i", "--image", required=True, help="原始照片路径")
    sync_parser.add_argument("--ref", required=True, help="参考构图图路径（目标效果）")
    sync_parser.add_argument("-p", "--prompt", help="指定提示词版本ID（默认全部）")
    sync_parser.add_argument("-r", "--repeats", type=int, default=1, help="每用例重复次数")
    sync_parser.add_argument("-d", "--delay", type=float, default=2.0, help="调用间隔（秒）")
    sync_parser.set_defaults(func=cmd_sync)

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
