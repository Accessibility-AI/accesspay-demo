"""Reset the database to the seed data: python -m mock_bank.seed"""

from mock_bank.config import Settings
from mock_bank.db import Database

if __name__ == "__main__":
    settings = Settings()
    Database(settings.db_path).reset()
    print(f"Seeded {settings.db_path}")
