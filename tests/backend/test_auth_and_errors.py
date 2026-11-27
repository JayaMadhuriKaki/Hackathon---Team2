"""API-001..003 and cross-cutting error rules: authentication, error shape, trace IDs (CLAUDE.md §11.3-11.4)."""

import pytest

from expenseflow_api import AUDITOR, EMPLOYEE, FIN_HEAD, MANAGER, PASSWORD, assert_error


def test_health_is_public_and_traced(api):
    r = api.get(None, "/health")
    assert r.status_code == 200
    assert r.headers.get("X-Trace-Id"), "every response carries X-Trace-Id"


def test_health_rejects_post_with_405(api):
    assert_error(api.post(None, "/health"), 405, "METHOD_NOT_ALLOWED")


def test_login_returns_bearer_token(api):
    r = api.login(EMPLOYEE)
    assert r.status_code == 200
    body = r.json()
    assert body["accessToken"] and body["tokenType"].lower() == "bearer"
    assert "passwordHash" not in r.text and "$2a$" not in r.text  # never echoes the BCrypt hash


def test_login_wrong_password_is_401(api):
    assert_error(api.login(EMPLOYEE, password=PASSWORD + "x"), 401, "UNAUTHENTICATED")


def test_login_missing_fields_is_400(api):
    assert_error(api.http.post("/auth/login", json={"email": "employee@expenseflow.test"}), 400, "VALIDATION_FAILED")


@pytest.mark.parametrize("token", [None, "not.a.token", "Bearer"])
def test_protected_endpoint_needs_valid_token(api, token):
    headers = {} if token is None else {"Authorization": f"Bearer {token}"}
    body = assert_error(api.http.get("/me", headers=headers), 401, "UNAUTHENTICATED")
    assert body["traceId"]


@pytest.mark.parametrize("who,role,is_admin,can_delegate", [
    (EMPLOYEE, "EMPLOYEE", False, False),
    (MANAGER, "EMPLOYEE", False, True),
    (AUDITOR, "FINANCE_AUDITOR", False, True),
    (FIN_HEAD, "FINANCE_HEAD", True, True),
])
def test_me_returns_role_and_capabilities(api, who, role, is_admin, can_delegate):
    r = api.get(who, "/me")
    assert r.status_code == 200
    me = r.json()
    assert me["role"] == role
    assert me["isAdmin"] is is_admin
    assert me["canDelegate"] is can_delegate
    assert "passwordHash" not in me and "password_hash" not in r.text


def test_departments_listed(api):
    r = api.get(EMPLOYEE, "/departments")
    assert r.status_code == 200
    assert len(r.json()) >= 3


def test_client_config_exposes_only_ui_values(api):
    r = api.get(EMPLOYEE, "/policy-rules/client-config")
    assert r.status_code == 200
    cfg = r.json()
    assert cfg["receiptMandatoryAbove"] == "500.00"
    assert {"MEALS", "HOTEL", "LOCAL_CAB"} <= set(cfg["categories"])
    assert "sample_audit.rate" not in r.text and "sampleAuditRate" not in cfg


def test_unknown_path_is_404_with_error_shape(api):
    assert_error(api.get(EMPLOYEE, "/no-such-endpoint"), 404)
