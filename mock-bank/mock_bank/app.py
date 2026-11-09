"""AccessPay mock bank API. Simulated funds only.

Run: uvicorn mock_bank.app:app --reload --port 8000
"""

import asyncio
import secrets
from typing import Literal

from fastapi import FastAPI, Header, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from mock_bank.config import FAULT_MODES, Settings
from mock_bank.db import Database, now_iso
from mock_bank.tokens import JwksKeyProvider, KeyProvider, TokenError, verify_confirmation

TRANSFER_ACTION_ID = "transfer_money"


class BankError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


class TransferRequest(BaseModel):
    beneficiary_id: str = Field(min_length=1, max_length=64)
    amount_minor: int = Field(gt=0, le=1_000_000_000, description="Amount in kobo")
    confirmation_token: str | None = Field(default=None, max_length=4096)
    # "accessai": confirmed through AccessAI, token required. "manual": the app's own Transfer screen.
    channel: Literal["accessai", "manual"] = "accessai"


class FaultRequest(BaseModel):
    mode: str
    timeout_seconds: float | None = Field(default=None, ge=0, le=120)


def _error(status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={"error": {"code": code, "message": message}})


def create_app(settings: Settings | None = None, keys: KeyProvider | None = None) -> FastAPI:
    settings = settings or Settings()
    db = Database(settings.db_path)
    keys = keys or JwksKeyProvider(settings.jwks_url)
    faults = {"mode": settings.fault_mode, "timeout_seconds": settings.fault_timeout_seconds}

    app = FastAPI(title="AccessPay mock bank", version="0.1.0", description="Simulated funds only.")
    app.state.db = db
    app.state.faults = faults

    @app.exception_handler(BankError)
    async def bank_error(_: Request, e: BankError):
        return _error(e.status, e.code, e.message)

    @app.exception_handler(RequestValidationError)
    async def validation_error(_: Request, e: RequestValidationError):
        first = e.errors()[0] if e.errors() else {}
        where = ".".join(str(p) for p in first.get("loc", ()) if p != "body")
        return _error(422, "invalid_request", f"{where}: {first.get('msg', 'invalid')}".strip(": "))

    @app.get("/healthz")
    def healthz():
        return {"status": "ok"}

    @app.get("/v1/balance")
    def balance():
        row = db.query("SELECT name, balance_minor FROM account WHERE id = 1")[0]
        return {"account_name": row["name"], "balance_minor": row["balance_minor"], "currency": "NGN", "as_of": now_iso()}

    @app.get("/v1/transactions")
    def transactions(limit: int = Query(default=20, ge=1, le=100)):
        rows = db.query(
            "SELECT id, kind, description, amount_minor, reference, created_at FROM transactions ORDER BY id DESC LIMIT ?",
            (limit,),
        )
        return {"items": [{**dict(r), "id": f"txn_{r['id']:04d}"} for r in rows]}

    @app.get("/v1/beneficiaries")
    def beneficiaries():
        rows = db.query("SELECT id, name, bank, account_last4 FROM beneficiaries ORDER BY name")
        return {"items": [dict(r) for r in rows]}

    @app.post("/v1/transfers", status_code=201)
    async def transfer(req: TransferRequest, idempotency_key: str | None = Header(default=None, max_length=128)):
        if not idempotency_key:
            raise BankError(400, "idempotency_key_required", "The Idempotency-Key header is required")

        # A retry of a request that already succeeded gets the original result, even after its token expired.
        previous = db.query("SELECT * FROM transfers WHERE idempotency_key = ?", (idempotency_key,))
        if previous:
            p = previous[0]
            if (p["beneficiary_id"], p["amount_minor"]) != (req.beneficiary_id, req.amount_minor):
                raise BankError(409, "idempotency_conflict", "This Idempotency-Key was used for a different transfer")
            return JSONResponse(status_code=200, content=_transfer_body(p, replayed=True))

        jti = _authorise(req, idempotency_key)

        if faults["mode"] == "timeout":
            await asyncio.sleep(faults["timeout_seconds"])
            raise BankError(504, "bank_timeout", "The bank did not respond in time. No money was moved.")
        if faults["mode"] == "error":
            raise BankError(500, "bank_error", "The bank had an internal error. No money was moved.")

        with db.transaction() as c:
            if jti is not None:
                if c.execute("SELECT 1 FROM used_tokens WHERE jti = ?", (jti,)).fetchone():
                    raise BankError(409, "token_replayed", "This confirmation has already been used")
                c.execute("INSERT INTO used_tokens (jti, used_at) VALUES (?, ?)", (jti, now_iso()))

        # Outcomes below consume the token even when no money moves: a new attempt needs a new confirmation.
        with db.transaction() as c:
            ben = c.execute("SELECT * FROM beneficiaries WHERE id = ?", (req.beneficiary_id,)).fetchone()
            if ben is None:
                raise BankError(404, "beneficiary_not_found", "That beneficiary does not exist")
            if faults["mode"] == "decline":
                raise BankError(402, "bank_declined", "Your bank declined this payment. No money left your account.")
            balance_minor = c.execute("SELECT balance_minor FROM account WHERE id = 1").fetchone()[0]
            if req.amount_minor > balance_minor:
                raise BankError(422, "insufficient_funds", "Your balance is too low for this transfer. No money was moved.")

            new_balance = balance_minor - req.amount_minor
            reference = f"AP-{secrets.randbelow(9000) + 1000}-{ben['account_last4']}"
            created_at = now_iso()
            c.execute("UPDATE account SET balance_minor = ? WHERE id = 1", (new_balance,))
            c.execute(
                "INSERT INTO transactions (kind, description, amount_minor, reference, created_at) VALUES ('debit', ?, ?, ?, ?)",
                (f"Transfer to {ben['name']}", req.amount_minor, reference, created_at),
            )
            c.execute(
                "INSERT INTO transfers VALUES (?, ?, ?, ?, ?, ?, ?)",
                (idempotency_key, reference, ben["id"], req.amount_minor, req.channel, new_balance, created_at),
            )
            row = c.execute("SELECT * FROM transfers WHERE idempotency_key = ?", (idempotency_key,)).fetchone()
        return _transfer_body(row, replayed=False)

    def _authorise(req: TransferRequest, idempotency_key: str) -> str | None:
        """Returns the token's jti for AccessAI transfers, None for manual ones."""
        if req.channel == "manual":
            if not settings.allow_manual_transfers:
                raise BankError(403, "manual_transfers_disabled", "Manual transfers are turned off")
            if req.confirmation_token:
                raise BankError(400, "invalid_request", "Manual transfers do not carry a confirmation token")
            return None
        if not req.confirmation_token:
            raise BankError(401, "confirmation_required", "An AccessAI confirmation token is required")
        try:
            claims = verify_confirmation(
                req.confirmation_token,
                action_id=TRANSFER_ACTION_ID,
                # The parameter names and resolved values exactly as the SDK confirmed them.
                params={"amount_minor": req.amount_minor, "recipient": req.beneficiary_id},
                keys=keys,
                allow_dev_tokens=settings.allow_dev_tokens,
            )
        except TokenError as e:
            status = 503 if e.code == "jwks_unavailable" else 401
            raise BankError(status, e.code, e.message) from e
        if claims["jti"] != idempotency_key:
            raise BankError(400, "idempotency_key_mismatch", "Idempotency-Key must be the confirmation token's nonce")
        return claims["jti"]

    if settings.admin_enabled:

        @app.get("/v1/_faults")
        def get_faults():
            return faults

        @app.post("/v1/_faults")
        def set_faults(req: FaultRequest):
            if req.mode not in FAULT_MODES:
                raise BankError(422, "invalid_request", f"mode must be one of {', '.join(FAULT_MODES)}")
            faults["mode"] = req.mode
            if req.timeout_seconds is not None:
                faults["timeout_seconds"] = req.timeout_seconds
            return faults

        @app.post("/v1/_reset")
        def reset():
            db.reset()
            faults["mode"] = "none"
            return {"status": "reset"}

    return app


def _transfer_body(row, *, replayed: bool) -> dict:
    return {
        "reference": row["reference"],
        "status": "completed",
        "beneficiary_id": row["beneficiary_id"],
        "amount_minor": row["amount_minor"],
        "balance_minor": row["balance_after_minor"],
        "channel": row["channel"],
        "created_at": row["created_at"],
        "replayed": replayed,
    }


def __getattr__(name: str):
    # `uvicorn mock_bank.app:app` builds the app on first access, so importing this module has no side effects.
    if name == "app":
        globals()["app"] = create_app()
        return globals()["app"]
    raise AttributeError(name)
