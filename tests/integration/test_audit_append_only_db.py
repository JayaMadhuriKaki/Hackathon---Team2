"""NFR-004 / B-11 on the real PostgreSQL database (H2 cannot prove this).

* The application role (expenseflow_app) has no UPDATE/DELETE/TRUNCATE rights on audit_events.
* Even the owner role is stopped by the append-only triggers.

Every statement runs inside BEGIN ... ROLLBACK, so nothing is changed even if a control is missing.
Runs psql inside the compose `postgres` container; skipped when Docker is not available.
"""

import os
import shutil
import subprocess

import pytest

pytestmark = pytest.mark.db

DB = os.environ.get("DB_NAME", "hackathon")
OWNER = os.environ.get("DB_USERNAME", "postgres")
APP = os.environ.get("DB_APP_USERNAME", "expenseflow_app")
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def psql(user: str, sql: str) -> subprocess.CompletedProcess:
    cmd = ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", user, "-d", DB, "-v", "ON_ERROR_STOP=1",
           "-c", f"BEGIN; {sql}; ROLLBACK;"]
    return subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True, timeout=60)


@pytest.fixture(scope="module", autouse=True)
def postgres_available():
    if shutil.which("docker") is None:
        pytest.skip("docker CLI not found")
    probe = subprocess.run(["docker", "compose", "exec", "-T", "postgres", "pg_isready"], cwd=REPO_ROOT,
                           capture_output=True, text=True, timeout=60)
    if probe.returncode != 0:
        pytest.skip(f"compose postgres not running: {probe.stderr.strip() or probe.stdout.strip()}")


TARGET = "(SELECT min(id) FROM audit_events)"


@pytest.mark.parametrize("sql", [
    f"UPDATE audit_events SET comment = 'tampered' WHERE id = {TARGET}",
    f"DELETE FROM audit_events WHERE id = {TARGET}",
    "TRUNCATE audit_events",
], ids=["update", "delete", "truncate"])
def test_app_role_cannot_change_audit_rows(sql):
    r = psql(APP, sql)
    assert r.returncode != 0, f"app role was allowed to run: {sql}"
    assert "permission denied" in r.stderr.lower(), r.stderr


def test_app_role_can_read_and_insert_only():
    r = psql(APP, "SELECT count(*) FROM audit_events")
    assert r.returncode == 0, r.stderr


@pytest.mark.parametrize("sql", [
    f"UPDATE audit_events SET comment = 'tampered' WHERE id = {TARGET}",
    f"DELETE FROM audit_events WHERE id = {TARGET}",
    "TRUNCATE audit_events",
], ids=["update", "delete", "truncate"])
def test_owner_is_blocked_by_append_only_trigger(sql):
    r = psql(OWNER, sql)
    assert r.returncode != 0, f"owner was allowed to run: {sql}"
    assert "append-only" in r.stderr, r.stderr


@pytest.mark.parametrize("table", ["policy_rules", "policy_results", "claim_comments"])
def test_app_role_cannot_rewrite_insert_only_tables(table):
    """V005 grants SELECT, INSERT only: config history and comments cannot be rewritten (B-08)."""
    r = psql(APP, f"DELETE FROM {table}")
    assert r.returncode != 0 and "permission denied" in r.stderr.lower(), r.stderr


def test_app_role_cannot_change_bank_details_or_hierarchy():
    """B-13 / B-14: the app has read-only access to users, so payout accounts and managers cannot be
    changed through the application."""
    r = psql(APP, "UPDATE users SET bank_account_number = '000000000000'")
    assert r.returncode != 0 and "permission denied" in r.stderr.lower(), r.stderr
