"""
Gemini 模型画像与配置适配。

说明：
- 这里既编码官方文档里的稳定能力，也兼容部分模型族的现实差异。
- 对不稳定或模型特有的参数，采用“仅在支持时下发”的保守策略。
"""
from dataclasses import dataclass
from typing import Optional, Tuple


@dataclass(frozen=True)
class GeminiModelProfile:
    model_name: str
    supports_image_input: bool
    supports_text_output: bool
    supports_image_output: bool
    supports_streaming_text: bool
    supports_thinking: bool
    supports_top_k: bool
    supports_max_output_tokens: bool
    supports_image_config: bool
    supports_image_size_control: bool
    default_response_modalities: tuple[str, ...]
    default_image_size: Optional[str] = None
    allowed_image_sizes: Tuple[str, ...] = ()


def _is_flash_text_family(model_name: str) -> bool:
    return (
        model_name.startswith("gemini-2.5-flash")
        or model_name.startswith("gemini-2.5-flash-lite")
        or model_name.startswith("gemini-2.5-pro")
        or model_name.startswith("gemini-2.0-flash")
    ) and "image" not in model_name


def _is_gemini_3_text_family(model_name: str) -> bool:
    return model_name.startswith("gemini-3") and "image" not in model_name


def get_gemini_model_profile(model_name: str) -> GeminiModelProfile:
    normalized = model_name.strip().lower()

    if normalized == "gemini-3.1-flash-image-preview":
        return GeminiModelProfile(
            model_name=model_name,
            supports_image_input=True,
            supports_text_output=True,
            supports_image_output=True,
            supports_streaming_text=True,
            supports_thinking=False,
            supports_top_k=True,
            supports_max_output_tokens=True,
            supports_image_config=True,
            supports_image_size_control=True,
            default_response_modalities=("TEXT", "IMAGE"),
            default_image_size="1K",
            allowed_image_sizes=("512", "1K", "2K", "4K"),
        )

    if normalized == "gemini-3-pro-image-preview":
        return GeminiModelProfile(
            model_name=model_name,
            supports_image_input=True,
            supports_text_output=True,
            supports_image_output=True,
            supports_streaming_text=True,
            supports_thinking=True,
            supports_top_k=True,
            supports_max_output_tokens=True,
            supports_image_config=True,
            supports_image_size_control=True,
            default_response_modalities=("TEXT", "IMAGE"),
            default_image_size="1K",
            allowed_image_sizes=("1K", "2K", "4K"),
        )

    if normalized == "gemini-2.5-flash-image":
        return GeminiModelProfile(
            model_name=model_name,
            supports_image_input=True,
            supports_text_output=True,
            supports_image_output=True,
            supports_streaming_text=True,
            supports_thinking=False,
            supports_top_k=True,
            supports_max_output_tokens=True,
            supports_image_config=True,
            supports_image_size_control=False,
            default_response_modalities=("TEXT", "IMAGE"),
            default_image_size=None,
            allowed_image_sizes=(),
        )

    if normalized == "gemini-2.0-flash-preview-image-generation":
        return GeminiModelProfile(
            model_name=model_name,
            supports_image_input=True,
            supports_text_output=True,
            supports_image_output=True,
            supports_streaming_text=True,
            supports_thinking=False,
            supports_top_k=True,
            supports_max_output_tokens=True,
            supports_image_config=True,
            supports_image_size_control=True,
            default_response_modalities=("TEXT", "IMAGE"),
            default_image_size="1K",
            allowed_image_sizes=("1K", "2K", "4K"),
        )

    if _is_flash_text_family(normalized):
        # 官方文档显示这类模型支持文本输出和多模态输入；
        # 这里对 max_output_tokens 采取保守策略，默认不下发给 2.5 flash / lite / pro，
        # 避免不同版本/别名上的兼容性与行为差异。
        supports_max_output_tokens = not normalized.startswith("gemini-2.5-")
        return GeminiModelProfile(
            model_name=model_name,
            supports_image_input=True,
            supports_text_output=True,
            supports_image_output=False,
            supports_streaming_text=True,
            supports_thinking=normalized.startswith("gemini-2.5-"),
            supports_top_k=True,
            supports_max_output_tokens=supports_max_output_tokens,
            supports_image_config=False,
            supports_image_size_control=False,
            default_response_modalities=("TEXT",),
            default_image_size=None,
            allowed_image_sizes=(),
        )

    if _is_gemini_3_text_family(normalized):
        return GeminiModelProfile(
            model_name=model_name,
            supports_image_input=True,
            supports_text_output=True,
            supports_image_output=False,
            supports_streaming_text=True,
            supports_thinking=True,
            supports_top_k=True,
            supports_max_output_tokens=False,
            supports_image_config=False,
            supports_image_size_control=False,
            default_response_modalities=("TEXT",),
            default_image_size=None,
            allowed_image_sizes=(),
        )

    # 未知模型：保守按“文本输出 + 多模态输入”处理，不下发高风险参数
    return GeminiModelProfile(
        model_name=model_name,
        supports_image_input=True,
        supports_text_output=True,
        supports_image_output=False,
        supports_streaming_text=True,
        supports_thinking=False,
        supports_top_k=False,
        supports_max_output_tokens=False,
        supports_image_config=False,
        supports_image_size_control=False,
        default_response_modalities=("TEXT",),
        default_image_size=None,
        allowed_image_sizes=(),
    )


def normalize_gemini_image_size(
    requested_size: Optional[str],
    profile: GeminiModelProfile,
) -> Optional[str]:
    """
    将用户输入标准化为 Gemini 官方 imageSize 值。

    官方支持值是 1K / 2K / 4K。
    这里兼容常见别名 1024/2048/4096，但不会把 512 伪装成可用值。
    """
    if not requested_size or not profile.supports_image_config or not profile.supports_image_size_control:
        return profile.default_image_size

    raw = requested_size.strip().upper()
    alias_map = {
        "512": "512",
        "1024": "1K",
        "1K": "1K",
        "2048": "2K",
        "2K": "2K",
        "4096": "4K",
        "4K": "4K",
    }
    normalized = alias_map.get(raw)
    if normalized is None:
        return profile.default_image_size

    if profile.allowed_image_sizes and normalized not in profile.allowed_image_sizes:
        return profile.default_image_size
    return normalized
