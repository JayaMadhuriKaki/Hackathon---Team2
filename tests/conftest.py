"""Shared fixtures for tests/backend and tests/integration.

Prerequisite: the Docker Compose stack is running with demo users seeded
(`docker compose up -d --build`; SEED_DEMO_USERS defaults to true). If the API is not reachable
the whole session is skipped with a clear message instead of failing every test.
"""

import httpx
import pytest

from expenseflow_api import BASE_URL, Api


def pytest_configure(config):
    config.addinivalue_line("markers", "tc(id): test-case ID")


@pytest.fixture(scope="session")
def api():
    try:
        health = httpx.get(f"{BASE_URL}/health", timeout=5)
    except httpx.HTTPError as exc:
        pytest.skip(f"ExpenseFlow API not reachable at {BASE_URL} ({exc.__class__.__name__}). "
                    "Start it with `docker compose up -d --build` or set API_BASE_URL.", allow_module_level=True)
    if health.status_code != 200:
        pytest.skip(f"API health returned {health.status_code}", allow_module_level=True)
    client = Api()
    yield client
    client.close()
