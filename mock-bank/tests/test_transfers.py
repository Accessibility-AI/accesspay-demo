import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from conftest import Minter, dev_token
from mock_bank.tokens import JwksKeyProvider, params_sha256


def send(client, token, jti, *, recipient="ben_001", amount_minor=500_000, key=None):
    return client.post(
        "/v1/transfers",
        json={"beneficiary_id": recipient, "amount_minor": amount_minor, "confirmation_token": token},
        headers={"Idempotency-Key": key or jti},
    )


def balance(client) -> int:
    return client.get("/v1/balance").json()["balance_minor"]


def code(response) -> str:
    return response.json()["error"]["code"]


def test_canonical_hash_matches_contract_reference_vector():
    assert params_sha256({"recipient": "ben_001", "amount_minor": 500000}) == (
        "b729a280eabf6c63b6a6b889bfa1b26d4dad23286f2e3a36b98028e35879cd47"
    )


def test_valid_transfer_moves_money_once(client, minter):
    token, jti = minter.token()
    r = send(client, token, jti)
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["balance_minor"] == 7_925_000
    assert body["reference"].startswith("AP-") and body["reference"].endswith("-2391")
    assert body["replayed"] is False
    assert balance(client) == 7_925_000
    latest = client.get("/v1/transactions").json()["items"][0]
    assert latest["description"] == "Transfer to David Adeyemi" and latest["amount_minor"] == 500_000


def test_retry_with_same_idempotency_key_returns_original_result(client, minter):
    token, jti = minter.token()
    first = send(client, token, jti).json()
    retry = send(client, token, jti)
    assert retry.status_code == 200
    assert retry.json()["reference"] == first["reference"] and retry.json()["replayed"] is True
    assert balance(client) == 7_925_000


def test_idempotency_key_reused_for_different_transfer_conflicts(client, minter):
    token, jti = minter.token()
    send(client, token, jti)
    r = send(client, token, jti, amount_minor=600_000)
    assert r.status_code == 409 and code(r) == "idempotency_conflict"


def test_idempotency_key_must_be_token_nonce(client, minter):
    token, jti = minter.token()
    r = send(client, token, jti, key="something-else")
    assert r.status_code == 400 and code(r) == "idempotency_key_mismatch"
    assert balance(client) == 8_425_000


@pytest.mark.parametrize("field,value", [("amount_minor", 5_000_000), ("recipient", "ben_002")])
def test_tampered_params_rejected(client, minter, field, value):
    token, jti = minter.token()
    r = send(client, token, jti, **{field: value})
    assert r.status_code == 401 and code(r) == "token_params_mismatch"
    assert balance(client) == 8_425_000


def test_expired_token_rejected(client, minter):
    token, jti = minter.token(ttl=-60)
    r = send(client, token, jti)
    assert r.status_code == 401 and code(r) == "token_expired"


def test_token_for_other_action_rejected(client, minter):
    token, jti = minter.token(aid="pay_bill")
    r = send(client, token, jti)
    assert r.status_code == 401 and code(r) == "token_action_mismatch"


def test_token_signed_by_other_key_rejected(client):
    token, jti = Minter(Ed25519PrivateKey.generate()).token()
    r = send(client, token, jti)
    assert r.status_code == 401 and code(r) == "token_invalid"


def test_token_with_unknown_kid_rejected(client, minter):
    claims = minter.claims()
    r = send(client, minter.sign(claims, kid="rotated-away"), claims["jti"])
    assert r.status_code == 401 and code(r) == "token_invalid"


def test_non_eddsa_token_rejected(client, minter):
    claims = minter.claims()
    r = send(client, minter.sign(claims, alg="none"), claims["jti"])
    assert r.status_code == 401 and code(r) == "token_invalid"


def test_token_from_wrong_issuer_rejected(client, minter):
    claims = minter.claims(iss="someone-else")
    r = send(client, minter.sign(claims), claims["jti"])
    assert r.status_code == 401 and code(r) == "token_invalid"


def test_malformed_token_rejected(client):
    r = send(client, "not-a-jws", "k1")
    assert r.status_code == 401 and code(r) == "token_malformed"


def test_missing_token_rejected(client):
    r = client.post("/v1/transfers", json={"beneficiary_id": "ben_001", "amount_minor": 500_000},
                    headers={"Idempotency-Key": "k1"})
    assert r.status_code == 401 and code(r) == "confirmation_required"


def test_missing_idempotency_key_rejected(client, minter):
    token, _ = minter.token()
    r = client.post("/v1/transfers", json={"beneficiary_id": "ben_001", "amount_minor": 500_000, "confirmation_token": token})
    assert r.status_code == 400 and code(r) == "idempotency_key_required"


@pytest.mark.parametrize("amount", [0, -100])
def test_non_positive_amount_rejected(client, amount):
    r = client.post("/v1/transfers", json={"beneficiary_id": "ben_001", "amount_minor": amount, "channel": "manual"},
                    headers={"Idempotency-Key": "k1"})
    assert r.status_code == 422 and code(r) == "invalid_request"


def test_insufficient_funds(client, minter):
    token, jti = minter.token(amount_minor=9_000_000)
    r = send(client, token, jti, amount_minor=9_000_000)
    assert r.status_code == 422 and code(r) == "insufficient_funds"
    assert balance(client) == 8_425_000


def test_unknown_beneficiary(client, minter):
    token, jti = minter.token(recipient="ben_999")
    r = send(client, token, jti, recipient="ben_999")
    assert r.status_code == 404 and code(r) == "beneficiary_not_found"


def test_dev_token_rejected_by_default(client, minter):
    claims = minter.claims()
    r = send(client, dev_token(claims), claims["jti"])
    assert r.status_code == 401 and code(r) == "dev_token_rejected"


def test_dev_token_accepted_in_local_dev_mode(make_client, minter):
    client = make_client(allow_dev_tokens=True)
    # LocalRulesBackend claims: sub, aid, ph, jti, exp (no iss).
    claims = {k: v for k, v in minter.claims().items() if k in ("sub", "aid", "ph", "jti", "exp")}
    r = send(client, dev_token(claims), claims["jti"])
    assert r.status_code == 201, r.text


def test_dev_token_still_checks_params(make_client, minter):
    client = make_client(allow_dev_tokens=True)
    claims = minter.claims()
    r = send(client, dev_token(claims), claims["jti"], amount_minor=1)
    assert r.status_code == 401 and code(r) == "token_params_mismatch"


def test_declined_transfer_consumes_token(client, minter):
    client.post("/v1/_faults", json={"mode": "decline"})
    token, jti = minter.token()
    r = send(client, token, jti)
    assert r.status_code == 402 and code(r) == "bank_declined"
    assert balance(client) == 8_425_000

    client.post("/v1/_faults", json={"mode": "none"})
    retry = send(client, token, jti)
    assert retry.status_code == 409 and code(retry) == "token_replayed"
    assert balance(client) == 8_425_000


def test_error_fault(client, minter):
    client.post("/v1/_faults", json={"mode": "error"})
    token, jti = minter.token()
    r = send(client, token, jti)
    assert r.status_code == 500 and code(r) == "bank_error"
    assert balance(client) == 8_425_000


def test_timeout_fault(client, minter):
    client.post("/v1/_faults", json={"mode": "timeout", "timeout_seconds": 0})
    token, jti = minter.token()
    r = send(client, token, jti)
    assert r.status_code == 504 and code(r) == "bank_timeout"
    assert balance(client) == 8_425_000


def test_invalid_fault_mode(client):
    r = client.post("/v1/_faults", json={"mode": "explode"})
    assert r.status_code == 422


def test_admin_endpoints_can_be_disabled(make_client):
    client = make_client(admin_enabled=False)
    assert client.post("/v1/_faults", json={"mode": "decline"}).status_code == 404


def test_manual_transfer(client):
    r = client.post("/v1/transfers", json={"beneficiary_id": "ben_003", "amount_minor": 200_000, "channel": "manual"},
                    headers={"Idempotency-Key": "manual-1"})
    assert r.status_code == 201 and r.json()["channel"] == "manual"
    assert balance(client) == 8_225_000


def test_manual_transfers_can_be_disabled(make_client):
    client = make_client(allow_manual_transfers=False)
    r = client.post("/v1/transfers", json={"beneficiary_id": "ben_003", "amount_minor": 200_000, "channel": "manual"},
                    headers={"Idempotency-Key": "manual-1"})
    assert r.status_code == 403 and code(r) == "manual_transfers_disabled"


def test_jwks_refetched_once_for_unknown_kid():
    calls = []
    old, new = Ed25519PrivateKey.generate(), Ed25519PrivateKey.generate()
    from conftest import jwks_for

    def fetch(_):
        calls.append(1)
        return jwks_for(old, "k1") if len(calls) == 1 else jwks_for(new, "k2")

    provider = JwksKeyProvider("http://x", fetch=fetch)
    assert provider.key("k1") is not None
    assert provider.key("k2") is not None
    assert len(calls) == 2


def test_jwks_unavailable_is_503(make_client, minter, tmp_path):
    from fastapi.testclient import TestClient
    from mock_bank.app import create_app
    from mock_bank.config import Settings

    def fail(_):
        raise ConnectionError("down")

    settings = Settings(db_path=str(tmp_path / "b.db"), allow_dev_tokens=False, fault_mode="none")
    client = TestClient(create_app(settings, JwksKeyProvider("http://x", fetch=fail)))
    token, jti = minter.token()
    r = send(client, token, jti)
    assert r.status_code == 503 and code(r) == "jwks_unavailable"
