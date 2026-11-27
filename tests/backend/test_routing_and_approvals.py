"""Approval routing and approver actions (FR-005..FR-007, FR-020, CL-02..CL-04, CL-09, CL-12, CL-16, B-02, B-05, B-06).

Boundaries per CLAUDE.md §16.2: 24,999.99 / 25,000.00 / 25,000.01 and 99,999.99 / 1,00,000.00 / 1,00,000.01.
"""

import uuid

import pytest

from expenseflow_api import (AUDITOR, AUDITOR2, AUTO_APPROVED_STATES, DATES, EMPLOYEE, EMPLOYEE2, FIN_HEAD, MANAGER,
                             NO_MANAGER, assert_error, uniq)

# ---------------------------------------------------------------- thresholds on the claim total (CL-02, CL-03)


@pytest.mark.parametrize("total", ["24999.99", "25000.00"])
def test_clean_claim_up_to_25000_is_auto_approved(api, total):
    claim = api.claim_of_total(EMPLOYEE, total)
    assert claim["status"] in AUTO_APPROVED_STATES
    assert api.current_steps(claim) == []


@pytest.mark.parametrize("total", ["25000.01", "99999.99", "100000.00"])
def test_above_25000_needs_manager_then_auditor(api, total):
    claim = api.claim_of_total(EMPLOYEE, total)
    assert claim["status"] == "PENDING_MANAGER"
    assert api.current_steps(claim) == ["MANAGER:PENDING", "FINANCE_AUDITOR:WAITING"]


def test_above_100000_adds_finance_head_and_walks_the_chain(api):
    claim = api.claim_of_total(EMPLOYEE, "100000.01")
    claim_id = claim["claimId"]
    assert api.current_steps(claim) == ["MANAGER:PENDING", "FINANCE_AUDITOR:WAITING", "FINANCE_HEAD:WAITING"]
    r = api.decide(MANAGER, claim_id, "approve", "ok by manager")
    assert r.status_code == 200 and r.json()["status"] == "PENDING_AUDITOR"
    r = api.decide(AUDITOR, claim_id, "approve")
    assert r.status_code == 200 and r.json()["status"] == "PENDING_FINANCE_HEAD"
    r = api.decide(FIN_HEAD, claim_id, "approve")
    assert r.status_code == 200 and r.json()["status"] == "APPROVED"
    actions = [e["action"] for e in api.all_items(EMPLOYEE, f"/claims/{claim_id}/audit-events")]
    assert "CLAIM_SUBMITTED" in actions and actions.count("STEP_APPROVED") == 3


def test_weekend_claim_under_25000_goes_to_manager_only(api):
    claim = api.claim_of_total(EMPLOYEE, "800.00", weekend=True)
    assert claim["status"] == "PENDING_MANAGER"
    assert api.current_steps(claim) == ["MANAGER:PENDING"]
    r = api.decide(MANAGER, claim["claimId"], "approve")
    assert r.json()["status"] == "APPROVED", "approved by manager: no sample draw for routed claims"


def test_claimant_without_manager_skips_to_auditor(api):
    """CL-09."""
    claim = api.claim_of_total(NO_MANAGER, "30000.00")
    assert claim["status"] == "PENDING_AUDITOR"
    assert api.current_steps(claim)[0] == "MANAGER:SKIPPED"


def test_finance_head_claim_goes_to_auditor_only(api):
    """CL-16: whatever the amount, never a finance-head step."""
    claim = api.claim_of_total(FIN_HEAD, "150000.00")
    assert claim["status"] == "PENDING_AUDITOR"
    active = [s for s in api.current_steps(claim) if not s.endswith("SKIPPED")]
    assert active == ["FINANCE_AUDITOR:PENDING"]
    assert api.decide(AUDITOR, claim["claimId"], "approve").json()["status"] == "APPROVED"


def test_auditor_claim_skips_own_auditor_step(api):
    """ASM-009: the auditor step of an auditor's own claim is skipped; the finance head is added."""
    claim = api.claim_of_total(AUDITOR, "30000.00")
    steps = api.current_steps(claim)
    assert "FINANCE_AUDITOR:SKIPPED" in steps and steps[-1].startswith("FINANCE_HEAD")


# ---------------------------------------------------------------- approver actions


@pytest.fixture
def pending_manager(api):
    return api.claim_of_total(EMPLOYEE, "30000.00")["claimId"]


def test_claimant_cannot_approve_own_claim(api):
    """B-02: the manager's own claim reaches the finance head; the manager cannot act on it."""
    claim = api.claim_of_total(MANAGER, "800.00", weekend=True)
    assert api.decide(MANAGER, claim["claimId"], "approve").status_code in (403, 404, 422)
    assert api.claim(MANAGER, claim["claimId"])["status"] == claim["status"]


def test_wrong_approver_cannot_act(api, pending_manager):
    """B-06: an auditor cannot approve a claim that is still at the manager step."""
    r = api.decide(AUDITOR, pending_manager, "approve")
    assert r.status_code in (403, 404)
    assert api.claim(EMPLOYEE, pending_manager)["status"] == "PENDING_MANAGER"


def test_other_employee_cannot_approve(api, pending_manager):
    assert_error(api.decide(EMPLOYEE2, pending_manager, "approve"), 404, "NOT_FOUND")


def test_double_approve_is_409(api):
    claim = api.claim_of_total(EMPLOYEE, "800.00", weekend=True)
    assert api.decide(MANAGER, claim["claimId"], "approve").status_code == 200
    assert_error(api.decide(MANAGER, claim["claimId"], "approve"), 409, "STATE_CONFLICT")


def test_approve_with_same_idempotency_key_is_replayed(api):
    claim = api.claim_of_total(EMPLOYEE, "800.00", weekend=True)
    key = str(uuid.uuid4())
    first = api.decide(MANAGER, claim["claimId"], "approve", key=key)
    again = api.decide(MANAGER, claim["claimId"], "approve", key=key)
    assert first.status_code == again.status_code == 200
    reused = api.decide(MANAGER, claim["claimId"], "approve", comment="different body", key=key)
    assert_error(reused, 422, "IDEMPOTENCY_KEY_REUSED")


@pytest.mark.parametrize("comment", [None, "", "   "])
def test_reject_needs_a_comment(api, pending_manager, comment):
    """FR-020, CL-04."""
    assert_error(api.decide(MANAGER, pending_manager, "reject", comment), 422, "COMMENT_REQUIRED")
    assert api.claim(EMPLOYEE, pending_manager)["status"] == "PENDING_MANAGER"


def test_reject_with_comment_ends_the_claim(api, pending_manager):
    r = api.decide(MANAGER, pending_manager, "reject", "Duplicate of an earlier trip")
    assert r.status_code == 200 and r.json()["status"] == "REJECTED"
    assert_error(api.decide(MANAGER, pending_manager, "approve"), 409, "STATE_CONFLICT")
    assert_error(api.submit(EMPLOYEE, pending_manager), 409, "STATE_CONFLICT")


def test_auditor_can_reject_at_auditor_step(api, pending_manager):
    api.decide(MANAGER, pending_manager, "approve")
    r = api.decide(AUDITOR2, pending_manager, "reject", "Receipt unreadable")
    assert r.json()["status"] == "REJECTED"


def test_comment_is_added_to_history(api, pending_manager):
    """FR-006."""
    r = api.post(MANAGER, f"/claims/{pending_manager}/comments", json={"body": "Please confirm the client name"})
    assert r.status_code == 201
    bodies = [c["body"] for c in api.claim(EMPLOYEE, pending_manager)["comments"]]
    assert "Please confirm the client name" in bodies
    assert_error(api.post(MANAGER, f"/claims/{pending_manager}/comments", json={"body": ""}), 400)


# ---------------------------------------------------------------- send back and restart (FR-007, CL-12, B-05)


def test_send_back_needs_a_comment(api, pending_manager):
    assert_error(api.decide(MANAGER, pending_manager, "send-back"), 422, "COMMENT_REQUIRED")


def test_send_back_restarts_chain_from_manager(api, pending_manager):
    assert api.decide(MANAGER, pending_manager, "approve").json()["status"] == "PENDING_AUDITOR"
    sent = api.decide(AUDITOR, pending_manager, "send-back", "Add the hotel invoice")
    assert sent.status_code == 200 and sent.json()["status"] == "SENT_BACK"
    detail = api.claim(EMPLOYEE, pending_manager)
    assert detail["latestSendBackComment"] == "Add the hotel invoice"
    again = api.submit(EMPLOYEE, pending_manager)
    assert again.status_code == 200
    claim = again.json()
    assert claim["status"] == "PENDING_MANAGER", "earlier approvals no longer count (CL-12)"
    assert claim["submissionNo"] == detail["submissionNo"] + 1
    assert api.current_steps(claim) == ["MANAGER:PENDING", "FINANCE_AUDITOR:WAITING"]


def test_send_back_then_lower_amount_reruns_policy(api, pending_manager):
    """B-05: re-routing follows the new total. The hotel line is edited down to 24,000.00."""
    api.decide(MANAGER, pending_manager, "send-back", "Split the stay correctly")
    detail = api.claim(EMPLOYEE, pending_manager)
    line = detail["lineItems"][0]
    body = {"category": "HOTEL", "amount": "24000.00", "expenseDate": line["expenseDate"],
            "vendor": uniq("TST Hotel"), "receiptNumber": uniq("TST-R"), "city": "Mumbai",
            "checkInDate": line["checkInDate"], "checkOutDate": line["checkOutDate"]}
    r = api.put(EMPLOYEE, f"/claims/{pending_manager}/line-items/{line['lineItemId']}", json=body)
    assert r.status_code == 200, r.text
    claim = api.submit(EMPLOYEE, pending_manager).json()
    assert claim["status"] in AUTO_APPROVED_STATES


def test_send_back_then_breach_is_auto_rejected(api, pending_manager):
    api.decide(MANAGER, pending_manager, "send-back", "Wrong category")
    api.line(EMPLOYEE, pending_manager, "MEALS", "1500.01", DATES.weekday())
    assert api.submit(EMPLOYEE, pending_manager).json()["status"] == "AUTO_REJECTED"


# ---------------------------------------------------------------- approvals inbox


def test_manager_inbox_lists_assigned_claims_only(api, pending_manager):
    assert pending_manager in {i["claimId"] for i in api.all_items(MANAGER, "/approvals")}
    assert api.get(EMPLOYEE, "/approvals").json()["items"] == []
