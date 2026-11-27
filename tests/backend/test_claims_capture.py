"""API-004..011: claim capture, field validation, receipts, locking after submit and visibility.

FR-001, FR-002, FR-003, FR-019, CL-15, B-04, B-10, NFR-005.
"""

import datetime as dt
import uuid

import pytest

from expenseflow_api import (AUTO_APPROVED_STATES, DATES, EMPLOYEE, EMPLOYEE2, FIN_HEAD, MANAGER, assert_error,
                             receipt_bytes, uniq)


def test_create_draft_claim(api):
    r = api.post(EMPLOYEE, "/claims")
    assert r.status_code == 201
    claim = r.json()
    assert claim["status"] == "DRAFT"
    assert claim["claimNumber"].startswith("CLM-")


def test_draft_with_lines_is_saved_and_listed(api):
    """FR-001/FR-002: several lines with category, amount, date and receipt; draft persists."""
    d = DATES.weekday()
    claim_id = api.new_claim(EMPLOYEE)
    api.line(EMPLOYEE, claim_id, "MEALS", "1200.00", d)
    api.line(EMPLOYEE, claim_id, "LOCAL_CAB", "1800.00", d)
    api.line(EMPLOYEE, claim_id, "HOTEL", "3500.00", d, city="Pune", nights=1)
    claim = api.claim(EMPLOYEE, claim_id)
    assert claim["status"] == "DRAFT"
    assert len(claim["lineItems"]) == 3
    assert claim["total"] == "6500.00"
    assert all(li["receipt"] for li in claim["lineItems"])
    hotel = next(li for li in claim["lineItems"] if li["category"] == "HOTEL")
    assert hotel["nights"] == 1 and hotel["city"] == "Pune"
    listed = api.get(EMPLOYEE, "/claims", params={"status": "DRAFT", "pageSize": 100}).json()
    assert {"items", "page", "pageSize", "total"} <= set(listed)


def test_draft_without_receipt_can_be_saved(api):
    """Receipt is checked at submit, not when saving a draft (ASM-003)."""
    claim_id = api.new_claim(EMPLOYEE)
    api.add_line(EMPLOYEE, claim_id, "MEALS", "900.00", DATES.weekday())
    assert api.claim(EMPLOYEE, claim_id)["status"] == "DRAFT"


@pytest.mark.parametrize("field,value", [
    ("amount", "0.00"),
    ("amount", "-100.00"),
    ("amount", "10.005"),
    ("vendor", ""),
    ("vendor", "V" * 121),
    ("receiptNumber", ""),
    ("receiptNumber", "R" * 61),
    ("category", "TRAVEL_AIR"),
])
def test_line_field_validation_is_400(api, field, value):
    claim_id = api.new_claim(EMPLOYEE)
    body = {"category": "MEALS", "amount": "100.00", "expenseDate": DATES.weekday().isoformat(),
            "vendor": uniq("TST V"), "receiptNumber": uniq("TST-R")}
    body[field] = value
    assert_error(api.post(EMPLOYEE, f"/claims/{claim_id}/line-items", json=body), 400, "VALIDATION_FAILED")


def test_future_expense_date_is_400(api):
    claim_id = api.new_claim(EMPLOYEE)
    future = dt.date.today() + dt.timedelta(days=3)
    r = api.add_line(EMPLOYEE, claim_id, "MEALS", "100.00", future, expect=None)
    body = assert_error(r, 400, "VALIDATION_FAILED")
    assert "expenseDate" in str(body["details"])


def test_hotel_without_city_and_dates_is_400(api):
    claim_id = api.new_claim(EMPLOYEE)
    body = {"category": "HOTEL", "amount": "3000.00", "expenseDate": DATES.weekday().isoformat(),
            "vendor": uniq("TST Hotel"), "receiptNumber": uniq("TST-R")}
    r = api.post(EMPLOYEE, f"/claims/{claim_id}/line-items", json=body)
    assert "city" in str(assert_error(r, 400, "VALIDATION_FAILED")["details"])


def test_hotel_zero_nights_is_400(api):
    claim_id = api.new_claim(EMPLOYEE)
    d = DATES.weekday()
    body = {"category": "HOTEL", "amount": "3000.00", "expenseDate": d.isoformat(), "vendor": uniq("TST Hotel"),
            "receiptNumber": uniq("TST-R"), "city": "Pune", "checkInDate": d.isoformat(),
            "checkOutDate": d.isoformat()}
    assert_error(api.post(EMPLOYEE, f"/claims/{claim_id}/line-items", json=body), 400, "VALIDATION_FAILED")


def test_mass_assignment_unknown_field_is_400(api):
    """Unknown JSON fields such as a status are rejected (CLAUDE.md §11.1, B-06)."""
    claim_id = api.new_claim(EMPLOYEE)
    body = {"category": "MEALS", "amount": "100.00", "expenseDate": DATES.weekday().isoformat(),
            "vendor": uniq("TST V"), "receiptNumber": uniq("TST-R"), "status": "APPROVED"}
    assert_error(api.post(EMPLOYEE, f"/claims/{claim_id}/line-items", json=body), 400, "VALIDATION_FAILED")


def test_update_and_delete_line_in_draft(api):
    d = DATES.weekday()
    claim_id = api.new_claim(EMPLOYEE)
    line_id = api.add_line(EMPLOYEE, claim_id, "MEALS", "300.00", d).json()["lineItemId"]
    body = {"category": "MEALS", "amount": "350.00", "expenseDate": d.isoformat(), "vendor": uniq("TST V"),
            "receiptNumber": uniq("TST-R")}
    r = api.put(EMPLOYEE, f"/claims/{claim_id}/line-items/{line_id}", json=body)
    assert r.status_code == 200 and r.json()["amount"] == "350.00"
    assert api.delete(EMPLOYEE, f"/claims/{claim_id}/line-items/{line_id}").status_code == 204
    assert api.claim(EMPLOYEE, claim_id)["lineItems"] == []


# ---------------------------------------------------------------- receipts


def test_receipt_upload_stores_type_and_is_downloadable(api):
    claim_id = api.new_claim(EMPLOYEE)
    line_id = api.add_line(EMPLOYEE, claim_id, "MEALS", "900.00", DATES.weekday()).json()["lineItemId"]
    content = receipt_bytes("png")
    up = api.upload(EMPLOYEE, claim_id, line_id, content, name="r.png", content_type="image/png")
    assert up.status_code == 201
    assert up.json()["contentType"] == "image/png"
    assert "path" not in up.text.lower(), "storage path never exposed"
    down = api.get(EMPLOYEE, f"/claims/{claim_id}/line-items/{line_id}/receipt")
    assert down.status_code == 200 and down.content == content


def test_receipt_type_checked_by_content_not_extension(api):
    """A text/HTML file renamed to .pdf is refused (CLAUDE.md §13.5)."""
    claim_id = api.new_claim(EMPLOYEE)
    line_id = api.add_line(EMPLOYEE, claim_id, "MEALS", "900.00", DATES.weekday()).json()["lineItemId"]
    r = api.upload(EMPLOYEE, claim_id, line_id, b"<html><script>alert(1)</script></html>", name="fake.pdf")
    assert_error(r, 415, "UNSUPPORTED_FILE_TYPE")


def test_receipt_over_size_limit_is_413(api):
    claim_id = api.new_claim(EMPLOYEE)
    line_id = api.add_line(EMPLOYEE, claim_id, "MEALS", "900.00", DATES.weekday()).json()["lineItemId"]
    big = b"%PDF-1.4\n" + b"0" * (5 * 1024 * 1024 + 10)
    r = api.upload(EMPLOYEE, claim_id, line_id, big)
    assert r.status_code == 413


# ---------------------------------------------------------------- submit validation


def test_submit_without_lines_is_422(api):
    claim_id = api.new_claim(EMPLOYEE)
    assert_error(api.submit(EMPLOYEE, claim_id), 422, "CLAIM_HAS_NO_LINES")


def test_receipt_required_above_500_blocks_submit(api):
    """POL-005 boundary: 500.00 without receipt is fine, 500.01 without receipt is blocked (ASM-003)."""
    d = DATES.weekday()
    claim_id = api.new_claim(EMPLOYEE)
    api.line(EMPLOYEE, claim_id, "MEALS", "500.00", d, receipt=False)
    missing = api.line(EMPLOYEE, claim_id, "LOCAL_CAB", "500.01", d, receipt=False)
    body = assert_error(api.submit(EMPLOYEE, claim_id), 422, "RECEIPT_REQUIRED")
    assert missing in str(body["details"])
    assert api.claim(EMPLOYEE, claim_id)["status"] == "DRAFT"
    assert api.upload(EMPLOYEE, claim_id, missing).status_code == 201
    r = api.submit(EMPLOYEE, claim_id)
    assert r.status_code == 200 and r.json()["status"] in AUTO_APPROVED_STATES


def test_receipt_check_runs_before_limit_check(api):
    """A cab line of 2,000.01 without a receipt gets RECEIPT_REQUIRED, not AUTO_REJECTED."""
    claim_id = api.new_claim(EMPLOYEE)
    api.line(EMPLOYEE, claim_id, "LOCAL_CAB", "2000.01", DATES.weekday(), receipt=False)
    assert_error(api.submit(EMPLOYEE, claim_id), 422, "RECEIPT_REQUIRED")


# ---------------------------------------------------------------- locking (B-04) and idempotency


def test_lines_locked_after_submit(api):
    d = DATES.weekday()
    claim_id = api.new_claim(EMPLOYEE)
    line_id = api.line(EMPLOYEE, claim_id, "MEALS", "400.00", d)
    assert api.submit(EMPLOYEE, claim_id).status_code == 200
    body = {"category": "MEALS", "amount": "1.00", "expenseDate": d.isoformat(), "vendor": uniq("TST V"),
            "receiptNumber": uniq("TST-R")}
    assert_error(api.post(EMPLOYEE, f"/claims/{claim_id}/line-items", json=body), 409, "STATE_CONFLICT")
    assert_error(api.put(EMPLOYEE, f"/claims/{claim_id}/line-items/{line_id}", json=body), 409, "STATE_CONFLICT")
    assert_error(api.delete(EMPLOYEE, f"/claims/{claim_id}/line-items/{line_id}"), 409, "STATE_CONFLICT")
    assert_error(api.upload(EMPLOYEE, claim_id, line_id), 409, "STATE_CONFLICT")
    assert_error(api.submit(EMPLOYEE, claim_id), 409, "STATE_CONFLICT")


def test_submit_with_same_idempotency_key_replays_response(api):
    claim_id = api.new_claim(EMPLOYEE)
    api.line(EMPLOYEE, claim_id, "MEALS", "450.00", DATES.weekday())
    key = str(uuid.uuid4())
    first = api.submit(EMPLOYEE, claim_id, key=key)
    second = api.submit(EMPLOYEE, claim_id, key=key)
    assert first.status_code == second.status_code == 200
    assert first.json()["status"] == second.json()["status"]
    audit = api.all_items(EMPLOYEE, f"/claims/{claim_id}/audit-events")
    assert sum(1 for e in audit if e["action"] == "CLAIM_SUBMITTED") == 1, "one transition only"


# ---------------------------------------------------------------- visibility (FR-019, CL-15, B-10)


@pytest.fixture(scope="module")
def employee_draft(api):
    claim_id = api.new_claim(EMPLOYEE)
    line_id = api.line(EMPLOYEE, claim_id, "MEALS", "700.00", DATES.weekday())
    return claim_id, line_id


@pytest.mark.parametrize("other", [EMPLOYEE2, MANAGER, FIN_HEAD])
def test_other_users_get_404_on_someone_elses_draft(api, employee_draft, other):
    """Not even the finance head can open a claim by role alone (CL-15)."""
    claim_id, line_id = employee_draft
    assert_error(api.get(other, f"/claims/{claim_id}"), 404, "NOT_FOUND")
    assert_error(api.get(other, f"/claims/{claim_id}/line-items/{line_id}/receipt"), 404, "NOT_FOUND")
    assert_error(api.get(other, f"/claims/{claim_id}/audit-events"), 404, "NOT_FOUND")


def test_other_employee_cannot_change_or_submit(api, employee_draft):
    claim_id, line_id = employee_draft
    body = {"category": "MEALS", "amount": "1.00", "expenseDate": DATES.weekday().isoformat(),
            "vendor": uniq("TST V"), "receiptNumber": uniq("TST-R")}
    assert_error(api.post(EMPLOYEE2, f"/claims/{claim_id}/line-items", json=body), 404)
    assert_error(api.put(EMPLOYEE2, f"/claims/{claim_id}/line-items/{line_id}", json=body), 404)
    assert_error(api.delete(EMPLOYEE2, f"/claims/{claim_id}/line-items/{line_id}"), 404)
    assert_error(api.upload(EMPLOYEE2, claim_id, line_id), 404)
    assert_error(api.submit(EMPLOYEE2, claim_id), 404)
    assert api.claim(EMPLOYEE, claim_id)["status"] == "DRAFT"


def test_claim_list_shows_only_own_claims(api, employee_draft):
    claim_id, _ = employee_draft
    for who in (EMPLOYEE2, FIN_HEAD):
        assert claim_id not in {c["claimId"] for c in api.all_items(who, "/claims")}


def test_unknown_and_malformed_claim_ids(api):
    assert_error(api.get(EMPLOYEE, f"/claims/{uuid.uuid4()}"), 404, "NOT_FOUND")
    assert api.get(EMPLOYEE, "/claims/not-a-uuid").status_code in (400, 404)
