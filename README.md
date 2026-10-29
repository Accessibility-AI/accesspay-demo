# AccessPay (demo)

A demonstration Nigerian fintech Android app that integrates the
[AccessAI Android SDK](https://github.com/Caculuz-cinks/accessai-android-sdk), plus a mock bank backend.
**Simulated funds only. No real money, bank accounts, PINs or OTPs.**

| Repo | What |
|---|---|
| [accessai-android-sdk](https://github.com/Caculuz-cinks/accessai-android-sdk) | Android SDK (built) |
| [accessai-server](https://github.com/Caculuz-cinks/accessai-server) | AI service |
| **accesspay-demo** (this) | Demo app + mock bank |

## What to build

```
app/          Kotlin + Jetpack Compose: Home (balance), Transfer, History, Beneficiaries
mock-bank/    Python FastAPI + SQLite: balance, transactions, beneficiaries, transfers
```

Seed data (matches the SDK tests and PRD examples): balance ₦84,250; beneficiaries David Adeyemi (…2391),
David Okafor (…7710), Amaka Obi (…5521).

### Mock bank endpoints

`GET /v1/balance`, `GET /v1/transactions`, `GET /v1/beneficiaries`, `POST /v1/transfers`.

`POST /v1/transfers` receives `beneficiary_id`, `amount_minor`, the AccessAI `confirmation_token` and an
`Idempotency-Key`. Before moving simulated money it must verify the token as described in the SDK's
[API contract](https://github.com/Caculuz-cinks/accessai-android-sdk/blob/main/docs/API_CONTRACT.md#get-well-knownjwksjson):
signature (JWKS), expiry, action id, the canonical params hash, and that the nonce was never used before. Reject
`dev-unsigned.` tokens unless running in explicit local dev mode. Add a fault-injection flag so failures and timeouts can
be demoed.

### Integrating the SDK

Follow [docs/INTEGRATION.md](https://github.com/Caculuz-cinks/accessai-android-sdk/blob/main/docs/INTEGRATION.md).
The SDK repo's `sample/` app is a minimal working integration to copy from. Register at least `get_balance`,
`navigate_to_transfer`, `transfer_money` and `get_transaction_history`, and add `AccessAIScreen(...)` to each screen.

```kotlin
implementation("com.github.Caculuz-cinks.accessai-android-sdk:accessai-compose:0.1.0")  // via JitPack
```

To build against SDK sources instead, add `includeBuild("../accessai-android-sdk")` to `settings.gradle.kts` with both
repos cloned side by side. CI detects this and checks the SDK out next to the app automatically.

## CI

Android compilation happens in GitHub Actions only; no local Android SDK is needed. Every push and pull request
builds the debug APK, runs unit tests and lint, and runs the mock bank's pytest suite. Download the APK from the
run's **Artifacts**. Jobs skip themselves with a notice until `gradlew` / `mock-bank/` exist.
