def test_seed_balance(client):
    body = client.get("/v1/balance").json()
    assert body["balance_minor"] == 8_425_000
    assert body["currency"] == "NGN"
    assert body["account_name"] == "Main account"


def test_beneficiaries_include_both_davids(client):
    items = client.get("/v1/beneficiaries").json()["items"]
    by_id = {b["id"]: b for b in items}
    assert by_id["ben_001"] == {"id": "ben_001", "name": "David Adeyemi", "bank": "GTBank", "account_last4": "2391"}
    assert by_id["ben_002"]["name"] == "David Okafor" and by_id["ben_002"]["account_last4"] == "7710"
    assert by_id["ben_003"]["name"] == "Amaka Obi" and by_id["ben_003"]["account_last4"] == "5521"


def test_transactions_newest_first_and_limited(client):
    items = client.get("/v1/transactions", params={"limit": 3}).json()["items"]
    assert len(items) == 3
    assert items[0]["description"] == "Transfer from Chinedu Okeke"
    assert [i["created_at"] for i in items] == sorted((i["created_at"] for i in items), reverse=True)


def test_reset_restores_seed(client):
    client.post("/v1/transfers", json={"beneficiary_id": "ben_003", "amount_minor": 100, "channel": "manual"},
                headers={"Idempotency-Key": "k1"})
    assert client.get("/v1/balance").json()["balance_minor"] == 8_424_900
    client.post("/v1/_reset")
    assert client.get("/v1/balance").json()["balance_minor"] == 8_425_000
