"""Verification of AccessAI confirmation tokens (SDK docs/API_CONTRACT.md, "GET /.well-known/jwks.json").

A token is a compact JWS signed with EdDSA (Ed25519). Before moving money the bank checks:
signature against the AccessAI JWKS, expiry, that `aid` is the action being run, that `ph` is the
SHA-256 of the canonical parameters it actually received, and (in the caller) that `jti` is unused.
"""

import base64
import binascii
import hashlib
import json
import time
from typing import Any, Callable, Protocol

import httpx
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

DEV_PREFIX = "dev-unsigned."
CLOCK_SKEW_SECONDS = 5


class TokenError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def canonical_json(params: dict[str, Any]) -> str:
    return json.dumps(params, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def params_sha256(params: dict[str, Any]) -> str:
    return hashlib.sha256(canonical_json(params).encode("utf-8")).hexdigest()


def b64url_decode(value: str) -> bytes:
    try:
        return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
    except (binascii.Error, ValueError) as e:
        raise TokenError("token_malformed", "Confirmation token is not valid base64url") from e


class KeyProvider(Protocol):
    def key(self, kid: str | None) -> Ed25519PublicKey | None: ...


class JwksKeyProvider:
    """Fetches the AccessAI JWKS and caches it. An unknown kid triggers one refetch (key rotation)."""

    def __init__(self, url: str, ttl_seconds: int = 300, fetch: Callable[[str], dict] | None = None):
        self._url = url
        self._ttl = ttl_seconds
        self._fetch = fetch or self._http_get
        self._keys: dict[str | None, Ed25519PublicKey] = {}
        self._fetched_at = 0.0

    @staticmethod
    def _http_get(url: str) -> dict:
        response = httpx.get(url, timeout=5)
        response.raise_for_status()
        return response.json()

    def _refresh(self) -> None:
        try:
            jwks = self._fetch(self._url)
        except Exception as e:  # network, HTTP or JSON errors
            raise TokenError("jwks_unavailable", "Could not fetch AccessAI signing keys") from e
        keys: dict[str | None, Ed25519PublicKey] = {}
        for k in jwks.get("keys", []):
            if k.get("kty") == "OKP" and k.get("crv") == "Ed25519" and "x" in k:
                keys[k.get("kid")] = Ed25519PublicKey.from_public_bytes(b64url_decode(k["x"]))
        self._keys = keys
        self._fetched_at = time.time()

    def key(self, kid: str | None) -> Ed25519PublicKey | None:
        if not self._keys or time.time() - self._fetched_at > self._ttl:
            self._refresh()
        if kid not in self._keys:
            self._refresh()
        return self._keys.get(kid)


def _decode_dev(token: str) -> dict[str, Any]:
    try:
        return json.loads(b64url_decode(token[len(DEV_PREFIX):]))
    except json.JSONDecodeError as e:
        raise TokenError("token_malformed", "Confirmation token payload is not JSON") from e


def _decode_signed(token: str, keys: KeyProvider) -> dict[str, Any]:
    parts = token.split(".")
    if len(parts) != 3:
        raise TokenError("token_malformed", "Confirmation token is not a compact JWS")
    header_b64, payload_b64, signature_b64 = parts
    try:
        header = json.loads(b64url_decode(header_b64))
        claims = json.loads(b64url_decode(payload_b64))
    except json.JSONDecodeError as e:
        raise TokenError("token_malformed", "Confirmation token header or payload is not JSON") from e
    if header.get("alg") != "EdDSA":
        raise TokenError("token_invalid", "Confirmation token must be signed with EdDSA")
    key = keys.key(header.get("kid"))
    if key is None:
        raise TokenError("token_invalid", "Confirmation token was signed with an unknown key")
    try:
        key.verify(b64url_decode(signature_b64), f"{header_b64}.{payload_b64}".encode("ascii"))
    except InvalidSignature as e:
        raise TokenError("token_invalid", "Confirmation token signature is invalid") from e
    if claims.get("iss") != "accessai":
        raise TokenError("token_invalid", "Confirmation token was not issued by AccessAI")
    return claims


def verify_confirmation(
    token: str,
    *,
    action_id: str,
    params: dict[str, Any],
    keys: KeyProvider,
    allow_dev_tokens: bool,
    now: float | None = None,
) -> dict[str, Any]:
    """Returns the token's claims if it authorises exactly this action with exactly these params."""
    if token.startswith(DEV_PREFIX):
        if not allow_dev_tokens:
            raise TokenError("dev_token_rejected", "Unsigned development tokens are not accepted")
        claims = _decode_dev(token)
    else:
        claims = _decode_signed(token, keys)

    now = time.time() if now is None else now
    exp = claims.get("exp")
    if not isinstance(exp, (int, float)):
        raise TokenError("token_invalid", "Confirmation token has no expiry")
    if now > exp + CLOCK_SKEW_SECONDS:
        raise TokenError("token_expired", "Confirmation token has expired")
    if claims.get("aid") != action_id:
        raise TokenError("token_action_mismatch", "Confirmation token is for a different action")
    if claims.get("ph") != params_sha256(params):
        raise TokenError("token_params_mismatch", "Confirmation token does not match this recipient and amount")
    if not isinstance(claims.get("jti"), str) or not claims["jti"]:
        raise TokenError("token_invalid", "Confirmation token has no nonce")
    return claims
