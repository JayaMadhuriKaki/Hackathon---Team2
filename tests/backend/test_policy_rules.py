"""POL-001..POL-009 end to end through submit (FR-004, CL-01, CL-05, CL-07, CL-08, ASM-003/004/006).

These mirror the JUnit policy tests over HTTP so the real config, database and routing are exercised.
"""

import datetime as dt

import pytest

from expenseflow_api import AUTO_APPROVED_STATES, DATES, EMPLOYEE, receipt_bytes, uniq


def results(claim, rule=None):
    found = [r for li in claim["lineItems"] for r in li["policyResults"]]
    return [r for r in found if rule is None or r["ruleId"] == rule]


def submit_one(api, category, amount, date=None, **kw):
    return api.submitted_claim(EMPLOYEE, [(category, amount, date or DATES.weekday(), kw)])


# ---------------------------------------------------------------- limits -> auto-reject (CL-07)


@pytest.mark.parametrize("category,amount,kw,rule,breach", [
    ("MEALS", "1500.00", {}, "POL-001", False),
    ("MEALS", "1500.01", {}, "POL-001", True),
    ("HOTEL", "6000.00", {"city": "Mumbai", "nights": 1}, "POL-002", False),
    ("HOTEL", "6000.01", {"city": "Mumbai", "nights": 1}, "POL-002", True),
    ("HOTEL", "12000.00", {"city": "Mumbai", "nights": 2}, "POL-002", False),
    ("HOTEL", "12000.01", {"city": "Mumbai", "nights": 2}, "POL-002", True),
    ("HOTEL", "4000.00", {"city": "Pune", "nights": 1}, "POL-003", False),
    ("HOTEL", "4000.01", {"city": "Pune", "nights": 1}, "POL-003", True),
    ("HOTEL", "12000.01", {"city": "Pune", "nights": 3}, "POL-003", True),
    ("LOCAL_CAB", "2000.00", {}, "POL-004", False),
    ("LOCAL_CAB", "2000.01", {}, "POL-004", True),
])
def test_limit_boundaries(api, category, amount, kw, rule, breach):
    claim = submit_one(api, category, amount, **kw)
    if breach:
        assert claim["status"] == "AUTO_REJECTED"
        fired = results(claim, rule)
        assert fired and fired[0]["resultType"] == "LIMIT_BREACH"
        assert fired[0]["configKey"] and fired[0]["configuredLimit"] and fired[0]["actualValue"] and fired[0]["reason"]
        assert claim["decisionReason"], "rejection reason recorded (CL-07)"
        assert api.current_steps(claim) == [], "an auto-rejected claim is never routed"
    else:
        assert claim["status"] in AUTO_APPROVED_STATES, claim["status"]
        assert results(claim) == []


@pytest.mark.parametrize("city", ["Kolkata", "Delhi", "Chennai", "Bangalore", "mumbai", "bengaluru", "  NEW DELHI "])
def test_tier1_cities_and_aliases_use_6000_limit(api, city):
    """CL-08, ASM-006: case-insensitive, trimmed, Bengaluru/New Delhi aliases."""
    claim = submit_one(api, "HOTEL", "5800.00", city=city, nights=1)
    assert claim["status"] in AUTO_APPROVED_STATES, (city, results(claim))


def test_city_not_on_the_list_uses_4000_limit(api):
    claim = submit_one(api, "HOTEL", "5000.00", city="Navi Mumbai", nights=1)
    assert claim["status"] == "AUTO_REJECTED"
    assert results(claim, "POL-003")


def test_meals_summed_per_day_within_one_claim(api):
    d = DATES.weekday()
    claim = api.submitted_claim(EMPLOYEE, [("MEALS", "900.00", d), ("MEALS", "700.00", d)])
    assert claim["status"] == "AUTO_REJECTED"
    assert results(claim, "POL-001")


def test_meals_summed_per_day_across_claims(api):
    """SPEC-Q-04 default: an earlier in-scope claim on the same date counts."""
    d = DATES.weekday()
    first = api.submitted_claim(EMPLOYEE, [("MEALS", "1000.00", d)])
    assert first["status"] in AUTO_APPROVED_STATES
    second = api.submitted_claim(EMPLOYEE, [("MEALS", "1000.00", d)])
    assert second["status"] == "AUTO_REJECTED"


def test_two_cab_trips_same_day_each_within_limit(api):
    d = DATES.weekday()
    claim = api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "1500.00", d), ("LOCAL_CAB", "1500.00", d)])
    assert claim["status"] in AUTO_APPROVED_STATES


def test_every_breaching_line_is_listed(api):
    """CL-07: the reason lists every breaching line."""
    d = DATES.weekday()
    claim = api.submitted_claim(EMPLOYEE, [("MEALS", "1600.00", d), ("HOTEL", "4500.00", d, {"city": "Pune"})])
    assert claim["status"] == "AUTO_REJECTED"
    assert results(claim, "POL-001") and results(claim, "POL-003")


# ---------------------------------------------------------------- weekend (POL-008, CL-02)


@pytest.mark.parametrize("offset", [0, 1], ids=["saturday", "sunday"])
def test_weekend_line_routes_to_manager(api, offset):
    sat = DATES.saturday()
    claim = submit_one(api, "MEALS", "800.00", date=sat + dt.timedelta(days=offset))
    assert claim["status"] == "PENDING_MANAGER"
    assert results(claim, "POL-008")[0]["resultType"] == "WEEKEND_FLAG"


def test_friday_and_monday_are_not_weekend(api):
    sat = DATES.saturday()
    claim = api.submitted_claim(EMPLOYEE, [("MEALS", "300.00", sat - dt.timedelta(days=1)),
                                          ("MEALS", "300.00", sat + dt.timedelta(days=2))])
    assert claim["status"] in AUTO_APPROVED_STATES


def test_breach_beats_weekend(api):
    """Evaluation order (§2.7): a breach auto-rejects even when a weekend flag is raised."""
    claim = submit_one(api, "MEALS", "1500.01", date=DATES.saturday())
    assert claim["status"] == "AUTO_REJECTED"


# ---------------------------------------------------------------- duplicates (POL-006/007/009, ASM-004)


def test_pol006_same_vendor_amount_date_is_flagged(api):
    d = DATES.weekday()
    vendor = uniq("TST Cab Co")
    api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "800.00", d, {"vendor": vendor})])
    dup = api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "800.00", d, {"vendor": vendor})])
    assert dup["status"] == "PENDING_MANAGER", "a duplicate is routed, never auto-approved"
    assert results(dup, "POL-006")[0]["resultType"] == "DUPLICATE_FLAG"


def test_pol006_different_amount_is_not_a_duplicate(api):
    d = DATES.weekday()
    vendor = uniq("TST Cab Co")
    api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "800.00", d, {"vendor": vendor})])
    other = api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "800.01", d, {"vendor": vendor})])
    assert other["status"] in AUTO_APPROVED_STATES


def test_pol007_renamed_receipt_with_same_bytes_is_flagged(api):
    """B-07: the duplicate check uses the file content hash, not the file name."""
    content = receipt_bytes()
    api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "650.00", DATES.weekday(), {"receipt": content})])
    claim_id = api.new_claim(EMPLOYEE)
    line_id = api.add_line(EMPLOYEE, claim_id, "LOCAL_CAB", "720.00", DATES.weekday()).json()["lineItemId"]
    assert api.upload(EMPLOYEE, claim_id, line_id, content, name="taxi-final-renamed.pdf").status_code == 201
    dup = api.submit(EMPLOYEE, claim_id).json()
    assert dup["status"] == "PENDING_MANAGER"
    assert results(dup, "POL-007")


def test_pol009_same_date_and_receipt_number_is_flagged(api):
    d = DATES.weekday()
    receipt_no = uniq("TST-R")
    api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "610.00", d, {"receipt_no": receipt_no})])
    dup = api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "990.00", d, {"receipt_no": receipt_no})])
    assert dup["status"] == "PENDING_MANAGER"
    assert results(dup, "POL-009")


def test_pol009_same_receipt_number_other_date_is_fine(api):
    receipt_no = uniq("TST-R")
    api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "610.00", DATES.weekday(), {"receipt_no": receipt_no})])
    other = api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "610.00", DATES.weekday(), {"receipt_no": receipt_no})])
    assert other["status"] in AUTO_APPROVED_STATES


def test_breach_plus_duplicate_is_auto_rejected(api):
    d = DATES.weekday()
    vendor = uniq("TST Cab Co")
    api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "800.00", d, {"vendor": vendor})])
    claim = api.submitted_claim(EMPLOYEE, [("LOCAL_CAB", "800.00", d, {"vendor": vendor}),
                                           ("MEALS", "1500.01", DATES.weekday())])
    assert claim["status"] == "AUTO_REJECTED"
