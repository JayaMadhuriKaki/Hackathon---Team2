"""Payout batch and mock bank-file export (FR-012..FR-014, CL-06, CL-11, CLAUDE.md §2.8, §13.4, B-09)."""

import csv
import datetime as dt
import hashlib
import io
import re
import uuid

import pytest

from expenseflow_api import AUDITOR, EMPLOYEE, FIN_HEAD, MANAGER, assert_error

COLUMNS = ["batch_number", "payment_reference", "value_date", "payment_mode", "employee_id", "beneficiary_name",
           "beneficiary_account_number", "beneficiary_ifsc", "amount", "currency", "claim_id", "department",
           "narration"]


def approved_claim(api, total="30000.00"):
    claim = api.claim_of_total(EMPLOYEE, total)
    api.decide(MANAGER, claim["claimId"], "approve")
    assert api.decide(AUDITOR, claim["claimId"], "approve").json()["status"] == "APPROVED"
    return claim


@pytest.mark.parametrize("who", [EMPLOYEE, MANAGER, AUDITOR])
def test_payout_is_for_finance_head_only(api, who):
    for path in ("/payout-batches", "/payout-batches/eligible-claims"):
        assert_error(api.get(who, path), 403, "FORBIDDEN")
    assert_error(api.post(who, "/payout-batches", json={"claimIds": [str(uuid.uuid4())]}), 403, "FORBIDDEN")


def test_unapproved_claim_cannot_be_batched(api):
    pending = api.claim_of_total(EMPLOYEE, "30000.00")["claimId"]
    assert_error(api.post(FIN_HEAD, "/payout-batches", json={"claimIds": [pending]}), 422,
                 "CLAIM_NOT_ELIGIBLE_FOR_PAYOUT")


def test_empty_batch_is_400(api):
    assert_error(api.post(FIN_HEAD, "/payout-batches", json={"claimIds": []}), 400, "VALIDATION_FAILED")


def test_batch_export_marks_paid_and_csv_matches_format(api):
    a, b = approved_claim(api, "30000.00"), approved_claim(api, "27450.00")
    ids = [a["claimId"], b["claimId"]]
    eligible = {c["claimId"] for c in api.all_items(FIN_HEAD, "/payout-batches/eligible-claims")}
    assert set(ids) <= eligible

    created = api.post(FIN_HEAD, "/payout-batches", json={"claimIds": ids})
    assert created.status_code == 201, created.text
    batch = created.json()
    batch_id = batch.get("batchId") or batch["batch"]["batchId"]
    batch_no = batch.get("batchNumber") or batch["batch"]["batchNumber"]
    assert re.fullmatch(r"PB-\d{4}-\d{4}", batch_no)
    assert api.claim(EMPLOYEE, ids[0])["status"] == "IN_PAYOUT_BATCH"

    # B-09: one claim, one batch
    assert_error(api.post(FIN_HEAD, "/payout-batches", json={"claimIds": [ids[0]]}), 422,
                 "CLAIM_NOT_ELIGIBLE_FOR_PAYOUT")
    # file only after export
    assert_error(api.get(FIN_HEAD, f"/payout-batches/{batch_id}/file"), 409, "STATE_CONFLICT")

    exported = api.post(FIN_HEAD, f"/payout-batches/{batch_id}/export")
    assert exported.status_code == 200, exported.text
    summary = exported.json()
    assert summary["rowCount"] == 2
    assert summary["totalAmount"] == "57450.00"
    assert summary["fileName"] == f"EXPENSEFLOW_PAYOUT_{batch_no}_{dt.date.today():%Y%m%d}.csv"
    for cid in ids:
        assert api.claim(EMPLOYEE, cid)["status"] == "PAID"  # CL-06
    assert_error(api.post(FIN_HEAD, f"/payout-batches/{batch_id}/export"), 409, "STATE_CONFLICT")

    first = api.get(FIN_HEAD, f"/payout-batches/{batch_id}/file")
    second = api.get(FIN_HEAD, f"/payout-batches/{batch_id}/file")
    assert first.status_code == 200 and first.content == second.content, "re-download is byte-identical"
    assert hashlib.sha256(first.content).hexdigest() == summary["fileSha256"]

    text = first.content.decode("utf-8")
    rows = list(csv.reader(io.StringIO(text)))
    assert rows[0] == COLUMNS
    assert len(rows) == 3
    header_line = text.splitlines()[0]
    assert header_line.startswith('"batch_number"'), "every text field quoted"
    by_claim = {r[10]: dict(zip(COLUMNS, r)) for r in rows[1:]}
    row = by_claim[a["claimNumber"]]
    assert row["batch_number"] == batch_no
    assert row["payment_reference"].startswith(batch_no + "-")
    assert row["payment_mode"] == "NEFT" and row["currency"] == "INR"
    assert row["amount"] == "30000.00"
    assert re.fullmatch(r"[A-Z]{4}0[A-Z0-9]{6}", row["beneficiary_ifsc"])
    assert len(row["narration"]) <= 30
    assert len({r[1] for r in rows[1:]}) == 2, "payment_reference unique per row"
    for r in rows[1:]:
        for value in r:
            assert not value.startswith(("=", "+", "-", "@", "\t", "\r")), f"formula injection: {value!r}"


def test_payout_actions_are_audited(api):
    claim = approved_claim(api, "26000.00")
    batch = api.post(FIN_HEAD, "/payout-batches", json={"claimIds": [claim["claimId"]]}).json()
    batch_id = batch.get("batchId") or batch["batch"]["batchId"]
    api.post(FIN_HEAD, f"/payout-batches/{batch_id}/export")
    actions = [e["action"] for e in api.all_items(EMPLOYEE, f"/claims/{claim['claimId']}/audit-events")]
    assert "CLAIM_ADDED_TO_BATCH" in actions and "CLAIM_PAID" in actions
