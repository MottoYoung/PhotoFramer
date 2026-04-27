"""
Stage 2 prompt 适配层。

原则：
- 不改变 Stage 1 已经决定的构图意图
- 只补不同生图 provider / model 更偏好的提示风格
"""


def adapt_prompt_for_stage2(
    *,
    provider_name: str,
    model_name: str,
    prompt: str,
    has_source_image: bool,
) -> str:
    cleaned = prompt.strip()
    if not cleaned:
        return cleaned

    normalized_provider = provider_name.strip().lower()
    normalized_model = model_name.strip().lower()

    if normalized_provider == "gemini":
        suffix = []
        if has_source_image:
            suffix.append("Preserve the original subject identity, scene layout, lighting logic, and material realism.")
            suffix.append("Treat this as a faithful image edit / composition reframing, not a new scene invention.")
        else:
            suffix.append("Keep the scene realistic and photographically coherent.")
        suffix.append("Do not add new subjects, remove real objects, or alter time, weather, or season.")
        return cleaned + "\n\n" + " ".join(suffix)

    if normalized_provider == "qwen":
        suffix = []
        if has_source_image:
            suffix.append("Faithfully preserve the original subject identity and environment while improving only the composition.")
        suffix.append("Keep the output photorealistic, natural, and free of hallucinated objects.")
        return cleaned + "\n\n" + " ".join(suffix)

    return cleaned
