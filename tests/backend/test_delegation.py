"""Delegation while on leave (FR-008, CL-13, ASM-007, B-02, B-03).

Delegations are global state, so every test cancels what it creates. Tests in this module run in order.
"""

import datetime as dt

import pytest

from expenseflow_api import AUDITOR, AUDITOR2, EMPLOYEE, EMPLOYEE2, FIN_HEAD, MANAGER, assert_error

TODAY = dt.date.today()


def cancel_given(api, who):
    for d in api.all_items(who, "/delegations"):
        if d["direction"] == "GIVEN" and d["cancelledAt"] is None and dt.date.fromisoformat(d["endDate"]) >= TODAY:
            api.delete(who, f"/delegations/{d['delegationId']}")


def create(api, who, delegate_who, start=TODAY, end=TODAY + dt.timedelta(days=7)):
    return api.post(who, "/delegations", json={"delegateId": api.user_id(delegate_who),
                                               "startDate": start.isoformat(), "endDate": end.isoformat()})


@pytest.fixture
def clean_manager(api):
    cancel_given(api, MANAGER)
    yield
    cancel_given(api, MANAGER)


def test_eligible_delegates_are_finance_roles_only(api):
    r = api.get(MANAGER, "/delegations/eligible-delegates")
    assert r.status_code == 200
    ids = {p["userId"] for p in r.json()}
    assert {api.user_id(AUDITOR), api.user_id(AUDITOR2), api.user_id(FIN_HEAD)} <= ids
    assert api.user_id(EMPLOYEE2) not in ids and api.user_id(MANAGER) not in ids, "no employees, never the caller"


def test_employee_without_reports_cannot_delegate(api):
    assert_error(create(api, EMPLOYEE, AUDITOR), 403, "FORBIDDEN")
    assert_error(api.get(EMPLOYEE, "/delegations/eligible-delegates"), 403, "FORBIDDEN")


@pytest.mark.parametrize("delegate", [EMPLOYEE2, MANAGER], ids=["plain-employee", "self"])
def test_invalid_delegates_are_refused(api, clean_manager, delegate):
    assert_error(create(api, MANAGER, delegate), 422, "INVALID_DELEGATE")


def test_end_before_start_is_400(api, clean_manager):
    assert_error(create(api, MANAGER, AUDITOR2, start=TODAY + dt.timedelta(days=3), end=TODAY), 400,
                 "VALIDATION_FAILED")


def test_delegate_approves_on_behalf_and_overlap_is_refused(api, clean_manager):
    made = create(api, MANAGER, AUDITOR2)
    assert made.status_code == 201, made.text
    assert_error(create(api, MANAGER, AUDITOR, start=TODAY + dt.timedelta(days=2),
                        end=TODAY + dt.timedelta(days=9)), 422, "DELEGATION_OVERLAP")

    claim = api.claim_of_total(EMPLOYEE, "800.00", weekend=True)
    inbox = {i["claimId"] for i in api.all_items(AUDITOR2, "/approvals")}
    assert claim["claimId"] in inbox, "delegated manager step shows in the delegate's inbox"
    r = api.decide(AUDITOR2, claim["claimId"], "approve", "approved while manager on leave")
    assert r.status_code == 200 and r.json()["status"] == "APPROVED"
    step = next(s for s in r.json()["steps"] if s["stepType"] == "MANAGER" and s["status"] == "APPROVED")
    assert step["actedBy"]["userId"] == api.user_id(AUDITOR2)
    assert step["onBehalfOf"]["userId"] == api.user_id(MANAGER)


def test_cancelled_delegation_removes_access(api, clean_manager):
    made = create(api, MANAGER, AUDITOR2).json()
    claim = api.claim_of_total(EMPLOYEE, "800.00", weekend=True)
    assert api.delete(MANAGER, f"/delegations/{made['delegationId']}").status_code == 204
    assert api.decide(AUDITOR2, claim["claimId"], "approve").status_code in (403, 404)
    assert api.decide(MANAGER, claim["claimId"], "approve").status_code == 200


def test_only_delegator_can_cancel(api, clean_manager):
    made = create(api, MANAGER, AUDITOR2).json()
    assert api.delete(AUDITOR2, f"/delegations/{made['delegationId']}").status_code in (403, 404)


def test_delegation_never_allows_self_approval(api):
    """B-02/B-03: the finance head (auditor's manager) delegates to the auditor; the auditor's own claim
    sits at the finance head's manager step and the auditor must not approve it."""
    cancel_given(api, FIN_HEAD)
    try:
        assert create(api, FIN_HEAD, AUDITOR).status_code == 201
        claim = api.claim_of_total(AUDITOR, "800.00", weekend=True)
        assert claim["status"] == "PENDING_MANAGER"
        r = api.decide(AUDITOR, claim["claimId"], "approve")
        assert r.status_code in (403, 404, 422)
        if r.status_code == 422:
            assert r.json()["code"] == "SELF_APPROVAL_FORBIDDEN"
        api.decide(FIN_HEAD, claim["claimId"], "approve")
    finally:
        cancel_given(api, FIN_HEAD)
