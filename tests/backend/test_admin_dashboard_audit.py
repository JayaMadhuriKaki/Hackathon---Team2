"""Dashboard, audit trail and policy configuration (FR-015..FR-018, CL-10, ASM-010, NFR-004, B-08, B-11)."""

import pytest

from expenseflow_api import AUDITOR, DATES, EMPLOYEE, FIN_HEAD, MANAGER, assert_error

# ---------------------------------------------------------------- dashboard


@pytest.mark.parametrize("path", ["/dashboard/spend", "/dashboard/policy-violations", "/dashboard/approval-time"])
@pytest.mark.parametrize("who,allowed", [(AUDITOR, True), (FIN_HEAD, True), (EMPLOYEE, False), (MANAGER, False)])
def test_dashboard_access(api, path, who, allowed):
    r = api.get(who, path)
    if allowed:
        assert r.status_code == 200, r.text
    else:
        assert_error(r, 403, "FORBIDDEN")


@pytest.mark.parametrize("group_by", ["category", "department", "month"])
def test_spend_grouped_by_category_department_month(api, group_by):
    r = api.get(FIN_HEAD, "/dashboard/spend", params={"groupBy": group_by})
    assert r.status_code == 200, r.text
    body = r.json()
    assert {"items", "grandTotal"} <= set(body)
    for item in body["items"]:
        assert {"key", "total", "lineCount"} <= set(item)
        assert "." in item["total"] and len(item["total"].split(".")[1]) == 2, "money as 2-decimal string"


def test_dashboard_bad_date_is_400(api):
    assert_error(api.get(FIN_HEAD, "/dashboard/spend", params={"fromDate": "31-12-2026"}), 400)


def test_violations_count_auto_rejections(api):
    before = api.get(AUDITOR, "/dashboard/policy-violations").json()["total"]
    api.submitted_claim(EMPLOYEE, [("MEALS", "1700.00", DATES.weekday())])
    after = api.get(AUDITOR, "/dashboard/policy-violations").json()["total"]
    assert after >= before + 1


def test_approval_time_report_shape(api):
    body = api.get(FIN_HEAD, "/dashboard/approval-time").json()
    assert {"approvedClaims", "averageHours"} <= set(body)


# ---------------------------------------------------------------- audit trail (FR-018, NFR-004, B-11)


@pytest.mark.parametrize("who", [EMPLOYEE, MANAGER, AUDITOR])
def test_full_audit_trail_is_finance_head_only(api, who):
    assert_error(api.get(who, "/audit-events"), 403, "FORBIDDEN")


def test_audit_trail_has_no_update_or_delete_path(api):
    events = api.get(FIN_HEAD, "/audit-events", params={"pageSize": 1}).json()["items"]
    assert events, "seeded stack has audit events"
    event_id = events[0]["eventId"]
    for method in ("PUT", "PATCH", "DELETE"):
        r = api.request(FIN_HEAD, method, f"/audit-events/{event_id}", json={})
        assert r.status_code in (404, 405), f"{method} must not exist"
        r = api.request(FIN_HEAD, method, "/audit-events", json={})
        assert r.status_code == 405


def test_audit_filter_by_action(api):
    items = api.get(FIN_HEAD, "/audit-events", params={"action": "CLAIM_SUBMITTED", "pageSize": 20}).json()["items"]
    assert items and all(e["action"] == "CLAIM_SUBMITTED" for e in items)
    assert_error(api.get(FIN_HEAD, "/audit-events", params={"action": "NOT_AN_ACTION"}), 400)


def test_failed_login_is_audited(api):
    api.login(EMPLOYEE, password="definitely-wrong")
    items = api.get(FIN_HEAD, "/audit-events", params={"action": "LOGIN_FAILED", "pageSize": 5}).json()["items"]
    assert items
    assert "definitely-wrong" not in str(items), "passwords never logged"


# ---------------------------------------------------------------- policy configuration (CL-10, B-08)


@pytest.mark.parametrize("who", [EMPLOYEE, MANAGER, AUDITOR])
def test_policy_rules_are_admin_only(api, who):
    assert_error(api.get(who, "/policy-rules"), 403, "FORBIDDEN")
    assert_error(api.put(who, "/policy-rules/meals.max_per_day", json={"value": "99999.00"}), 403, "FORBIDDEN")


def test_policy_rules_list_all_keys(api):
    keys = {r["ruleKey"] for r in api.get(FIN_HEAD, "/policy-rules").json()}
    assert {"meals.max_per_day", "hotel.max_per_night.tier1", "hotel.max_per_night.other", "hotel.tier1_cities",
            "local_cab.max_per_trip", "receipt.mandatory_above", "approval.auditor_threshold",
            "approval.finance_head_threshold", "sample_audit.rate"} <= keys


@pytest.mark.parametrize("key,value", [
    ("meals.max_per_day", "-1.00"),
    ("sample_audit.rate", "0"),
    ("sample_audit.rate", "1.01"),
    ("approval.finance_head_threshold", "20000.00"),  # must stay above the auditor threshold
])
def test_invalid_policy_values_are_400(api, key, value):
    assert_error(api.put(FIN_HEAD, f"/policy-rules/{key}", json={"value": value}), 400, "VALIDATION_FAILED")


def test_unknown_rule_key_is_404(api):
    assert_error(api.put(FIN_HEAD, "/policy-rules/meals.unlimited", json={"value": "1.00"}), 404, "NOT_FOUND")


@pytest.mark.config_change
def test_config_change_is_audited_and_does_not_reroute_in_flight_claims(api):
    """B-08: a 30,000 claim at the manager keeps its auditor step even after the threshold is raised."""
    claim = api.claim_of_total(EMPLOYEE, "30000.00")
    rules = {r["ruleKey"]: r for r in api.get(FIN_HEAD, "/policy-rules").json()}
    old = rules["approval.auditor_threshold"]["value"]
    try:
        r = api.put(FIN_HEAD, "/policy-rules/approval.auditor_threshold", json={"value": "50000.00"})
        assert r.status_code == 200, r.text
        assert r.json()["configVersion"] > rules["approval.auditor_threshold"]["configVersion"]
        assert api.decide(MANAGER, claim["claimId"], "approve").json()["status"] == "PENDING_AUDITOR"
        changed = api.get(FIN_HEAD, "/audit-events", params={"action": "POLICY_RULE_CHANGED", "pageSize": 5})
        assert changed.json()["items"]
    finally:
        api.put(FIN_HEAD, "/policy-rules/approval.auditor_threshold", json={"value": old})
