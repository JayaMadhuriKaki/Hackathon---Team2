"""Auditor review queue, bulk approve and the 10% sample audit (FR-009..FR-011, CL-14, B-12)."""

import uuid

import pytest

from expenseflow_api import AUDITOR, AUDITOR2, EMPLOYEE, FIN_HEAD, MANAGER, assert_error


def at_auditor(api, total="30000.00"):
    claim = api.claim_of_total(EMPLOYEE, total)
    assert api.decide(MANAGER, claim["claimId"], "approve").json()["status"] == "PENDING_AUDITOR"
    return claim["claimId"]


@pytest.mark.parametrize("who", [EMPLOYEE, MANAGER, FIN_HEAD])
def test_review_queue_is_for_auditors_only(api, who):
    assert_error(api.get(who, "/review-queue"), 403, "FORBIDDEN")
    assert_error(api.post(who, "/review-queue/bulk-approve", json={"claimIds": [str(uuid.uuid4())]}), 403,
                 "FORBIDDEN")


def test_queue_lists_auditor_step_claims_and_filters(api):
    claim_id = at_auditor(api)
    everything = {i["claimId"]: i for i in api.all_items(AUDITOR, "/review-queue")}
    assert everything[claim_id]["queue"] == "AUDITOR_STEP"
    hotel = {i["claimId"] for i in api.all_items(AUDITOR, "/review-queue", {"category": "HOTEL"})}
    assert claim_id in hotel
    meals = {i["claimId"] for i in api.all_items(AUDITOR, "/review-queue", {"category": "MEALS"})}
    assert claim_id not in meals
    big = {i["claimId"] for i in api.all_items(AUDITOR, "/review-queue", {"minAmount": "30000.01"})}
    assert claim_id not in big


@pytest.mark.parametrize("param,value", [("flag", "NOPE"), ("queue", "EVERYTHING"), ("minAmount", "abc")])
def test_bad_queue_filter_is_400(api, param, value):
    assert_error(api.get(AUDITOR, "/review-queue", params={param: value}), 400)


def test_queue_never_shows_auditors_own_claims(api):
    own = api.claim_of_total(AUDITOR, "30000.00")["claimId"]
    assert own not in {i["claimId"] for i in api.all_items(AUDITOR, "/review-queue")}


def test_bulk_approve_reports_per_claim_results(api):
    ok1, ok2 = at_auditor(api), at_auditor(api)
    still_at_manager = api.claim_of_total(EMPLOYEE, "30000.00")["claimId"]
    unknown = str(uuid.uuid4())
    r = api.post(AUDITOR2, "/review-queue/bulk-approve", json={"claimIds": [ok1, ok2, still_at_manager, unknown]})
    assert r.status_code == 200, r.text
    body = r.json()
    outcome = {x["claimId"]: x for x in body["results"]}
    assert outcome[ok1]["outcome"] == outcome[ok2]["outcome"] == "APPROVED"
    assert outcome[still_at_manager]["outcome"] == "FAILED", "bulk approve cannot skip the manager step (B-06)"
    assert outcome[unknown]["outcome"] == "FAILED"
    assert body["approved"] == 2 and body["failed"] == 2
    assert api.claim(EMPLOYEE, ok1)["status"] == "APPROVED"
    assert api.claim(EMPLOYEE, still_at_manager)["status"] == "PENDING_MANAGER"


@pytest.mark.parametrize("ids", [[], [str(uuid.uuid4()) for _ in range(101)]], ids=["empty", "101-ids"])
def test_bulk_approve_size_limits(api, ids):
    assert_error(api.post(AUDITOR, "/review-queue/bulk-approve", json={"claimIds": ids}), 400, "VALIDATION_FAILED")


# ---------------------------------------------------------------- sample audit (needs a config change)


@pytest.fixture
def sample_everything(api):
    """Sets sample_audit.rate to 1.00 so a clean claim is always sampled; restores the old value."""
    rules = api.get(FIN_HEAD, "/policy-rules").json()
    rules = rules["items"] if isinstance(rules, dict) else rules
    old = next(r["value"] for r in rules if r["ruleKey"] == "sample_audit.rate")
    r = api.put(FIN_HEAD, "/policy-rules/sample_audit.rate", json={"value": "1.00"})
    assert r.status_code == 200, r.text
    yield
    api.put(FIN_HEAD, "/policy-rules/sample_audit.rate", json={"value": old})


@pytest.mark.config_change
def test_sampled_claim_is_held_from_payout_until_audited(api, sample_everything):
    claim = api.claim_of_total(EMPLOYEE, "900.00")
    claim_id = claim["claimId"]
    assert claim["status"] == "SAMPLE_AUDIT_PENDING"
    eligible = {c["claimId"] for c in api.all_items(FIN_HEAD, "/payout-batches/eligible-claims")}
    assert claim_id not in eligible, "CL-14: held from payout"
    assert_error(api.post(FIN_HEAD, "/payout-batches", json={"claimIds": [claim_id]}), 422,
                 "CLAIM_NOT_ELIGIBLE_FOR_PAYOUT")
    queue = {i["claimId"]: i for i in api.all_items(AUDITOR, "/review-queue", {"queue": "SAMPLE_AUDIT"})}
    assert queue[claim_id]["queue"] == "SAMPLE_AUDIT"
    assert api.decide(AUDITOR, claim_id, "approve").json()["status"] == "APPROVED"
    eligible = {c["claimId"] for c in api.all_items(FIN_HEAD, "/payout-batches/eligible-claims")}
    assert claim_id in eligible


@pytest.mark.config_change
def test_sampled_claim_rejection_needs_comment(api, sample_everything):
    claim_id = api.claim_of_total(EMPLOYEE, "950.00")["claimId"]
    assert_error(api.decide(AUDITOR, claim_id, "reject"), 422, "COMMENT_REQUIRED")
    assert api.decide(AUDITOR, claim_id, "reject", "Receipt does not match").json()["status"] == "REJECTED"


@pytest.mark.config_change
def test_auditor_cannot_clear_own_sampled_claim(api, sample_everything):
    """B-12 / B-02: the auditor's own sampled claim is not in their queue and cannot be approved by them."""
    claim_id = api.claim_of_total(AUDITOR, "700.00")["claimId"]
    assert claim_id not in {i["claimId"] for i in api.all_items(AUDITOR, "/review-queue")}
    assert api.decide(AUDITOR, claim_id, "approve").status_code in (403, 404, 422)
    assert api.decide(AUDITOR2, claim_id, "approve").json()["status"] == "APPROVED"


def test_sample_draw_is_recorded_in_audit(api):
    """B-12: every auto-approval records whether it was sampled."""
    claim = api.claim_of_total(EMPLOYEE, "820.00")
    actions = {e["action"] for e in api.all_items(EMPLOYEE, f"/claims/{claim['claimId']}/audit-events")}
    assert actions & {"SAMPLE_AUDIT_SELECTED", "SAMPLE_AUDIT_NOT_SELECTED"}
