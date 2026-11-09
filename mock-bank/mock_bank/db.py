"""SQLite storage. Amounts are integers in kobo (1 naira = 100 kobo)."""

import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime, timezone

SCHEMA = """
CREATE TABLE IF NOT EXISTS account (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    name TEXT NOT NULL,
    balance_minor INTEGER NOT NULL CHECK (balance_minor >= 0)
);
CREATE TABLE IF NOT EXISTS beneficiaries (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    bank TEXT NOT NULL,
    account_last4 TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL CHECK (kind IN ('credit', 'debit')),
    description TEXT NOT NULL,
    amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
    reference TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS transfers (
    idempotency_key TEXT PRIMARY KEY,
    reference TEXT NOT NULL UNIQUE,
    beneficiary_id TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    channel TEXT NOT NULL,
    balance_after_minor INTEGER NOT NULL,
    created_at TEXT NOT NULL
);
-- Confirmation token ids (jti) already presented, whatever the outcome. A token works at most once.
CREATE TABLE IF NOT EXISTS used_tokens (
    jti TEXT PRIMARY KEY,
    used_at TEXT NOT NULL
);
"""

SEED_BALANCE_MINOR = 8_425_000  # ₦84,250

SEED_BENEFICIARIES = [
    ("ben_001", "David Adeyemi", "GTBank", "2391"),
    ("ben_002", "David Okafor", "Access Bank", "7710"),
    ("ben_003", "Amaka Obi", "Zenith Bank", "5521"),
    ("ben_004", "Chinedu Okeke", "First Bank", "3304"),
    ("ben_005", "Halima Bello", "UBA", "8812"),
]

# Oldest first, so ids increase with time.
SEED_TRANSACTIONS = [
    ("credit", "Salary from Lagos Logistics Ltd", 25_000_000, "2026-10-25T09:00:00Z"),
    ("debit", "Transfer to Amaka Obi", 1_500_000, "2026-10-27T18:42:00Z"),
    ("debit", "Electricity token, Ikeja Electric", 1_000_000, "2026-10-29T08:15:00Z"),
    ("debit", "MTN airtime", 200_000, "2026-11-01T12:30:00Z"),
    ("credit", "Transfer from Chinedu Okeke", 500_000, "2026-11-03T16:05:00Z"),
]


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class Database:
    def __init__(self, path: str):
        self._conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._conn.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        self._conn.executescript(SCHEMA)
        if self._conn.execute("SELECT COUNT(*) FROM account").fetchone()[0] == 0:
            self.reset()

    @contextmanager
    def transaction(self):
        """One writer at a time; rolls back on any exception."""
        with self._lock:
            self._conn.execute("BEGIN IMMEDIATE")
            try:
                yield self._conn
            except BaseException:
                self._conn.execute("ROLLBACK")
                raise
            self._conn.execute("COMMIT")

    def query(self, sql: str, params: tuple = ()) -> list[sqlite3.Row]:
        with self._lock:
            return self._conn.execute(sql, params).fetchall()

    def reset(self) -> None:
        with self.transaction() as c:
            for table in ("account", "beneficiaries", "transactions", "transfers", "used_tokens"):
                c.execute(f"DELETE FROM {table}")
            if self._has_sequence(c):
                c.execute("DELETE FROM sqlite_sequence WHERE name = 'transactions'")
            c.execute("INSERT INTO account (id, name, balance_minor) VALUES (1, 'Main account', ?)", (SEED_BALANCE_MINOR,))
            c.executemany("INSERT INTO beneficiaries VALUES (?, ?, ?, ?)", SEED_BENEFICIARIES)
            c.executemany(
                "INSERT INTO transactions (kind, description, amount_minor, created_at) VALUES (?, ?, ?, ?)",
                SEED_TRANSACTIONS,
            )

    @staticmethod
    def _has_sequence(c: sqlite3.Connection) -> bool:
        return c.execute("SELECT 1 FROM sqlite_master WHERE name = 'sqlite_sequence'").fetchone() is not None

    def close(self) -> None:
        self._conn.close()
