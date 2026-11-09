import base64
import json
import time
import uuid

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from fastapi.testclient import TestClient

from mock_bank.app import create_app
from mock_bank.config import Settings
from mock_bank.tokens import JwksKeyProvider, params_sha256

KID = "test-key-1"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def jwks_for(private_key: Ed25519PrivateKey, kid: str = KID) -> dict:
    x = private_key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    return {"keys": [{"kty": "OKP", "crv": "Ed25519", "kid": kid, "use": "sig", "alg": "EdDSA", "x": b64url(x)}]}


class Minter:
    """Mints tokens the way the AccessAI service does (app/crypto/jws_signer.py)."""

    def __init__(self, private_key: Ed25519PrivateKey, kid: str = KID):
        self.private_key = private_key
        self.kid = kid

    def claims(self, *, recipient="ben_001", amount_minor=500_000, aid="transfer_money", ttl=60, jti=None, **extra):
        now = int(time.time())
        return {
            "iss": "accessai",
            "sub": "s_test",
            "aid": aid,
            "ph": params_sha256({"amount_minor": amount_minor, "recipient": recipient}),
            "jti": jti or uuid.uuid4().hex,
            "amr": ["biometric"],
            "iat": now,
            "exp": now + ttl,
            **extra,
        }

    def sign(self, claims: dict, *, alg="EdDSA", kid=None) -> str:
        header = b64url(json.dumps({"alg": alg, "typ": "JWT", "kid": kid or self.kid}).encode())
        payload = b64url(json.dumps(claims).encode())
        signature = b64url(self.private_key.sign(f"{header}.{payload}".encode()))
        return f"{header}.{payload}.{signature}"

    def token(self, **kw) -> tuple[str, str]:
        claims = self.claims(**kw)
        return self.sign(claims), claims["jti"]


def dev_token(claims: dict) -> str:
    return "dev-unsigned." + b64url(json.dumps(claims).encode())


@pytest.fixture
def signing_key() -> Ed25519PrivateKey:
    return Ed25519PrivateKey.generate()


@pytest.fixture
def minter(signing_key) -> Minter:
    return Minter(signing_key)


@pytest.fixture
def make_client(tmp_path, signing_key):
    def make(**overrides) -> TestClient:
        settings = Settings(
            db_path=str(tmp_path / f"bank-{uuid.uuid4().hex}.db"),
            jwks_url="http://accessai.test/.well-known/jwks.json",
            allow_dev_tokens=False,
            allow_manual_transfers=True,
            admin_enabled=True,
            fault_mode="none",
        )
        for k, v in overrides.items():
            setattr(settings, k, v)
        keys = JwksKeyProvider(settings.jwks_url, fetch=lambda _: jwks_for(signing_key))
        return TestClient(create_app(settings, keys))

    return make


@pytest.fixture
def client(make_client) -> TestClient:
    return make_client()
