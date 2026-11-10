# AccessPay (demo)

A demonstration Nigerian fintech Android app that integrates the
[AccessAI Android SDK](https://github.com/Accessibility-AI/accessai-android-sdk), plus a mock bank backend.
**Simulated funds only. No real money, bank accounts, PINs or OTPs.**

| Repo | What |
|---|---|
| [accessai-android-sdk](https://github.com/Accessibility-AI/accessai-android-sdk) | Android SDK (built) |
| [accessai-server-gemini](https://github.com/Accessibility-AI/accessai-server-gemini) | AI service (Gemini) |
| **accesspay-demo** (this) | Demo app + mock bank |

## What's here

```
app/          Kotlin + Jetpack Compose: Home (balance), Transfer, History, Beneficiaries, AccessAI mode
mock-bank/    Python FastAPI + SQLite: balance, transactions, beneficiaries, transfers
```

Seed data (matches the SDK tests and PRD examples): balance ₦84,250; beneficiaries David Adeyemi (…2391),
David Okafor (…7710), Amaka Obi (…5521), Chinedu Okeke (…3304), Halima Bello (…8812).

## Try it on a phone

Download `accesspay-debug-apk` from the latest green CI run on `main` (**Actions → run → Artifacts**), unzip it and
install `app-debug.apk`. The phone needs internet and a screen lock (transfers ask for it). Nothing else has to run:
by default the app talks to the live AccessAI service and uses a **simulated bank inside the app** (same seed data and
rules as `mock-bank/`, reset when the app restarts). It checks each confirmation token's action, recipient and amount
hash, expiry and single-use nonce, but not its signature; `mock-bank/` verifies that against the service's JWKS. Each
CI build is signed with a different debug key, so uninstall the previous build before installing a new one.

## Run it locally

**1. Mock bank** (optional; Python 3.11+). Only needed to demo a separate bank backend that verifies token signatures:

```bash
cd mock-bank && python3 -m venv .venv && .venv/bin/pip install -e ".[dev]"
```

```bash
cd mock-bank && .venv/bin/uvicorn mock_bank.app:app --host 0.0.0.0 --port 8000
```

By default the bank verifies confirmation tokens against the live AccessAI service,
`https://accessai-server-gemini.fly.dev/.well-known/jwks.json` (override with `ACCESSAI_JWKS_URL`).
`ALLOW_DEV_TOKENS=true` is only for building the app with an empty `accessaiUrl` (the SDK's offline mode, unsigned
tokens). See `mock-bank/.env.example`.

**2. Android app** (JDK 17, Android SDK 35; emulator or device with a screen lock set, since transfers need it):

```bash
./gradlew installDebug
```

| Gradle property | Default | Meaning |
|---|---|---|
| `accessaiUrl` | `https://accessai-server-gemini.fly.dev` | AccessAI service URL. `-PaccessaiUrl=` (empty) = the SDK's offline `LocalRulesBackend` (keyword matching, unsigned tokens; start the bank with `ALLOW_DEV_TOKENS=true`). |
| `mockBankUrl` | empty | Empty = the simulated bank inside the app. Otherwise the mock bank's URL: `http://10.0.2.2:8000` is your machine from the emulator; on a USB device run `adb reverse tcp:8000 tcp:8000` and pass `-PmockBankUrl=http://localhost:8000`. |
| `localSdk` | unset | `true` builds against `../accessai-android-sdk` sources instead of JitPack. |

Example: `./gradlew installDebug -PmockBankUrl=http://192.168.1.20:8000 -PlocalSdk=true`. Debug builds allow plain HTTP,
so a phone on the same Wi-Fi can use `-PmockBankUrl=http://<your-computer-ip>:8000` (start the bank with `--host 0.0.0.0`).

## Mock bank API

Amounts are integers in kobo. Errors are `{"error": {"code", "message"}}`.

| Endpoint | Notes |
|---|---|
| `GET /v1/balance` | `balance_minor`, `account_name`, `as_of` |
| `GET /v1/transactions?limit=20` | Newest first |
| `GET /v1/beneficiaries` | `id`, `name`, `bank`, `account_last4` |
| `POST /v1/transfers` | Body `beneficiary_id`, `amount_minor`, `confirmation_token`, `channel`; header `Idempotency-Key` |
| `GET` / `POST /v1/_faults` | Fault injection: `{"mode": "none" \| "decline" \| "timeout" \| "error", "timeout_seconds": 30}` |
| `POST /v1/_reset` | Back to the seed data |

**AccessAI transfers** (`channel: "accessai"`) are verified as the SDK's
[API contract](https://github.com/Accessibility-AI/accessai-android-sdk/blob/main/docs/API_CONTRACT.md#get-well-knownjwksjson)
requires: EdDSA signature against the JWKS, `iss`, expiry, `aid == "transfer_money"`, `ph` equal to the SHA-256 of
`{"amount_minor": …, "recipient": <beneficiary_id>}` in canonical JSON, and a `jti` never seen before. The
`Idempotency-Key` must be that `jti`. A retry with the same key returns the original result; a token is spent by any
attempt that reaches the ledger, including declines, so trying again needs a new confirmation. `dev-unsigned.` tokens are
rejected unless `ALLOW_DEV_TOKENS=true`.

**Manual transfers** (`channel: "manual"`, no token) come from the app's own Transfer screen, after an on-screen review
and Confirm. There is no login in this demo; turn them off with `ALLOW_MANUAL_TRANSFERS=false`.

Fault injection, e.g. to demo the failure screen: `curl -X POST localhost:8000/v1/_faults -H 'content-type: application/json' -d '{"mode":"decline"}'`.
Admin endpoints are disabled with `ADMIN_ENABLED=false`.

## How the app uses the SDK

Following [docs/INTEGRATION.md](https://github.com/Accessibility-AI/accessai-android-sdk/blob/main/docs/INTEGRATION.md),
with `accessai-compose:0.2.2` from JitPack:

- `AccessPayApp` initialises AccessAI and registers `get_balance`, `navigate_to_transfer`, `get_transaction_history` and
  `transfer_money` (`app/src/main/kotlin/com/accesspay/demo/ai/AccessPayActions.kt`).
- `transfer_money` resolves the spoken name against saved beneficiaries (two Davids → AccessAI asks which), checks the
  balance, and forwards the confirmation token and nonce to the bank. Only the bank's answer decides what is announced.
- `MainActivity` is a `FragmentActivity` (the SDK's biometric prompt needs it), shows the consent screen on first use,
  and publishes a `ScreenDescriptor` for whichever screen is current.

## CI

Android compilation also runs in GitHub Actions, so a local Android SDK is optional. Every push and pull request
builds the debug APK, runs unit tests and lint, and runs the mock bank's pytest suite. Download the APK from the
run's **Artifacts**. Jobs skip themselves with a notice until `gradlew` / `mock-bank/` exist.
