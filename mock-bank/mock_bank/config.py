import os
from dataclasses import dataclass, field

FAULT_MODES = ("none", "decline", "timeout", "error")


def _flag(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).strip().lower() in ("1", "true", "yes", "on")


@dataclass
class Settings:
    db_path: str = field(default_factory=lambda: os.getenv("MOCK_BANK_DB", "mock_bank.db"))
    jwks_url: str = field(default_factory=lambda: os.getenv("ACCESSAI_JWKS_URL", "https://accessai-server-gemini.fly.dev/.well-known/jwks.json"))
    # The SDK's offline backend mints "dev-unsigned." tokens. Accepting them is local development only.
    allow_dev_tokens: bool = field(default_factory=lambda: _flag("ALLOW_DEV_TOKENS", False))
    # Transfers made from the app's own Transfer screen (not AccessAI) carry no confirmation token.
    allow_manual_transfers: bool = field(default_factory=lambda: _flag("ALLOW_MANUAL_TRANSFERS", True))
    # /v1/_faults and /v1/_reset, for driving demos.
    admin_enabled: bool = field(default_factory=lambda: _flag("ADMIN_ENABLED", True))
    fault_mode: str = field(default_factory=lambda: os.getenv("FAULT_MODE", "none"))
    fault_timeout_seconds: float = field(default_factory=lambda: float(os.getenv("FAULT_TIMEOUT_SECONDS", "30")))

    def __post_init__(self) -> None:
        if self.fault_mode not in FAULT_MODES:
            raise ValueError(f"FAULT_MODE must be one of {', '.join(FAULT_MODES)}")
