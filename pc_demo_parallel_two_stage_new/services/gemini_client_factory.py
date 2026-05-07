"""
Gemini Client 工厂。

支持两种模式：
1. 官方 Gemini API（GEMINI_API_KEY）
2. 国内 Gemini 兼容中转（Authorization: Bearer + 自定义 base_url）
"""
import os
import socket
import ssl
from contextlib import contextmanager
from urllib.parse import urlparse

from google import genai
from google.genai import types

from config import (
    GEMINI_API_KEY,
    GEMINI_DOMESTIC_API_KEY,
    GEMINI_DOMESTIC_API_VERSION,
    GEMINI_DOMESTIC_BASE_URL,
    GEMINI_PROXY_URL,
    USE_GEMINI_DOMESTIC_API,
    USE_GEMINI_PROXY,
)


def _normalize_base_url(url: str) -> str:
    return url.strip().rstrip("/") + "/"


def _mask_token(token: str | None) -> str:
    if not token:
        return "<missing>"
    stripped = token.strip()
    if len(stripped) <= 8:
        return stripped
    return f"{stripped[:4]}...{stripped[-4:]}"


def _mask_proxy(proxy_url: str | None) -> str:
    if not proxy_url:
        return "<disabled>"
    return proxy_url.strip()


def _proxy_enabled() -> bool:
    return USE_GEMINI_PROXY and bool(GEMINI_PROXY_URL)


def is_official_gemini_api_enabled() -> bool:
    return not USE_GEMINI_DOMESTIC_API


@contextmanager
def _without_official_gemini_env():
    """
    国内代理模式下，避免 google-genai SDK 自动读取官方环境变量并注入 x-goog-api-key。
    """
    keys = ("GEMINI_API_KEY", "GOOGLE_API_KEY")
    original = {key: os.environ.get(key) for key in keys}
    try:
        for key in keys:
            os.environ.pop(key, None)
        yield
    finally:
        for key, value in original.items():
            if value is not None:
                os.environ[key] = value


@contextmanager
def _with_gemini_proxy_env():
    """
    只在创建 Gemini client 时为其注入代理环境变量，避免影响 Qwen / 其他 provider。
    """
    if not USE_GEMINI_PROXY or not GEMINI_PROXY_URL:
        yield
        return

    keys = (
        "HTTP_PROXY",
        "HTTPS_PROXY",
        "ALL_PROXY",
        "http_proxy",
        "https_proxy",
        "all_proxy",
    )
    original = {key: os.environ.get(key) for key in keys}
    try:
        for key in keys:
            os.environ[key] = GEMINI_PROXY_URL
        yield
    finally:
        for key, value in original.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


def _build_domestic_http_options() -> types.HttpOptions:
    return types.HttpOptions(
        base_url=_normalize_base_url(GEMINI_DOMESTIC_BASE_URL),
        api_version=GEMINI_DOMESTIC_API_VERSION or "v1beta",
        headers={
            "Authorization": f"Bearer {GEMINI_DOMESTIC_API_KEY.strip()}",
        },
    )


def _strip_sdk_api_key_header(client: genai.Client) -> genai.Client:
    headers = getattr(client._api_client._http_options, "headers", None)
    if isinstance(headers, dict) and "x-goog-api-key" in headers:
        headers.pop("x-goog-api-key", None)
    return client


def create_gemini_client() -> genai.Client:
    proxy_enabled = _proxy_enabled()
    if USE_GEMINI_DOMESTIC_API:
        if not GEMINI_DOMESTIC_BASE_URL:
            raise ValueError("请设置 GEMINI_DOMESTIC_BASE_URL 环境变量")
        if not GEMINI_DOMESTIC_API_KEY:
            raise ValueError("请设置 GEMINI_DOMESTIC_API_KEY 环境变量")
        official_env_present = any(
            os.environ.get(key) for key in ("GEMINI_API_KEY", "GOOGLE_API_KEY")
        )
        print(
            "🔐 Gemini 国内代理模式启用 "
            f"[base={_normalize_base_url(GEMINI_DOMESTIC_BASE_URL)}] "
            f"[api_version={GEMINI_DOMESTIC_API_VERSION or 'v1beta'}] "
            f"[bearer={_mask_token(GEMINI_DOMESTIC_API_KEY)}] "
            f"[local_proxy={_mask_proxy(GEMINI_PROXY_URL) if proxy_enabled else '<disabled>'}] "
            f"[official_env_present={official_env_present}]",
            flush=True,
        )
        with _without_official_gemini_env(), _with_gemini_proxy_env():
            client = genai.Client(
                api_key=GEMINI_DOMESTIC_API_KEY.strip(),
                http_options=_build_domestic_http_options()
            )
        client = _strip_sdk_api_key_header(client)
        print(
            f"🔐 Gemini 国内代理请求头已清理 [x-goog-api-key stripped={'x-goog-api-key' not in client._api_client._http_options.headers}]",
            flush=True,
        )
        return client

    if not GEMINI_API_KEY:
        raise ValueError("请设置 GEMINI_API_KEY 环境变量")
    print(
        "🔐 Gemini 官方模式启用 "
        f"[api_key={_mask_token(GEMINI_API_KEY)}] "
        f"[local_proxy={_mask_proxy(GEMINI_PROXY_URL) if proxy_enabled else '<disabled>'}]",
        flush=True,
    )
    with _with_gemini_proxy_env():
        return genai.Client(api_key=GEMINI_API_KEY)


def describe_gemini_backend() -> str:
    proxy_suffix = ""
    if _proxy_enabled():
        proxy_suffix = f" via {_mask_proxy(GEMINI_PROXY_URL)}"
    if USE_GEMINI_DOMESTIC_API:
        base = GEMINI_DOMESTIC_BASE_URL.strip() if GEMINI_DOMESTIC_BASE_URL else "<missing>"
        version = GEMINI_DOMESTIC_API_VERSION or "v1beta"
        return f"domestic-proxy {base} [{version}]{proxy_suffix}"
    return f"official Gemini API{proxy_suffix}"


def _recv_until(sock: socket.socket, marker: bytes, limit: int = 8192) -> bytes:
    chunks: list[bytes] = []
    total = 0
    while total < limit:
        chunk = sock.recv(4096)
        if not chunk:
            break
        chunks.append(chunk)
        total += len(chunk)
        if marker in chunk or marker in b"".join(chunks):
            break
    return b"".join(chunks)


def probe_gemini_proxy_connectivity(
    target_host: str = "generativelanguage.googleapis.com",
    target_port: int = 443,
    timeout_seconds: float = 6.0,
) -> tuple[bool, str]:
    if not _proxy_enabled():
        return False, "Gemini 代理未启用"

    parsed = urlparse(GEMINI_PROXY_URL)
    scheme = (parsed.scheme or "http").lower()
    host = parsed.hostname
    port = parsed.port

    if not host or not port:
        return False, f"代理地址无效: {GEMINI_PROXY_URL}"

    if scheme not in {"http", "https"}:
        return False, f"当前启动自检仅支持 HTTP/HTTPS 代理: {GEMINI_PROXY_URL}"

    proxy_addr = f"{host}:{port}"
    try:
        with socket.create_connection((host, port), timeout=timeout_seconds) as raw_sock:
            raw_sock.settimeout(timeout_seconds)
            tunnel_sock: socket.socket = raw_sock

            if scheme == "https":
                proxy_ctx = ssl.create_default_context()
                tunnel_sock = proxy_ctx.wrap_socket(raw_sock, server_hostname=host)

            connect_request = (
                f"CONNECT {target_host}:{target_port} HTTP/1.1\r\n"
                f"Host: {target_host}:{target_port}\r\n"
                "Proxy-Connection: Keep-Alive\r\n\r\n"
            ).encode("ascii")
            tunnel_sock.sendall(connect_request)
            response = _recv_until(tunnel_sock, b"\r\n\r\n")
            status_line = response.split(b"\r\n", 1)[0].decode("latin-1", errors="replace")
            if " 200 " not in status_line:
                return False, f"代理 CONNECT 失败 [{proxy_addr}] -> {status_line}"

            tls_ctx = ssl.create_default_context()
            tls_sock = tls_ctx.wrap_socket(tunnel_sock, server_hostname=target_host)
            try:
                tls_sock.do_handshake()
                cipher = tls_sock.cipher()
                protocol = tls_sock.version()
            finally:
                tls_sock.close()

            return (
                True,
                f"代理连通成功 [{proxy_addr}] -> {target_host}:{target_port} "
                f"[tls={protocol} cipher={cipher[0] if cipher else 'unknown'}]",
            )
    except Exception as error:
        return False, (
            f"代理连通失败 [{proxy_addr}] -> {target_host}:{target_port} "
            f"error={type(error).__name__}: {error}"
        )
