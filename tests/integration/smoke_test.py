"""End-to-end smoke test against a running ExpenseFlow stack (CI deploy job and local use).

Standard library only. Usage:
    py -3.13 tests/integration/smoke_test.py
Environment: API_BASE (default http://localhost:8080/api/v1), PROXY_BASE (default
http://localhost:3000/api/v1), DEMO_PASSWORD (default: the Compose demo default).
Exits non-zero on the first failed check.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = os.environ.get("API_BASE", "http://localhost:8080/api/v1")
PROXY = os.environ.get("PROXY_BASE", "http://localhost:3000/api/v1")
PASSWORD = os.environ.get("DEMO_PASSWORD", "Demo-Only-2026!")


def call(method, url, token=None, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw[:1] in "{[" else raw
    except urllib.error.HTTPError as err:
        raw = err.read().decode()
        return err.code, json.loads(raw) if raw[:1] in "{[" else raw
    except OSError as err:
        return None, str(err)


def check(name, condition, detail=""):
    print(("PASS " if condition else "FAIL ") + name + ("" if condition else f"  -> {detail}"))
    if not condition:
        sys.exit(1)


def past_saturday():
    """A different past Saturday per run (weekend -> routed to the manager), so repeated runs do
    not add up past the meals daily limit (POL-001) on one date."""
    import datetime
    import random
    today = datetime.date.today()
    last_sat = today - datetime.timedelta(days=(today.weekday() - 5) % 7 or 7)
    return (last_sat - datetime.timedelta(weeks=random.randint(2, 150))).isoformat()


def wait_for_health(seconds=180):
    deadline = time.time() + seconds
    while time.time() < deadline:
        status, body = call("GET", API + "/health")
        if status == 200 and isinstance(body, dict) and body.get("database") == "UP":
            return body
        time.sleep(3)
    return None


def login(who):
    status, body = call("POST", API + "/auth/login",
                        body={"email": f"{who}@expenseflow.test", "password": PASSWORD})
    check(f"login {who}", status == 200, f"{status} {body}")
    return body["accessToken"]


def main():
    check("health UP with database", wait_for_health() is not None)
    status, body = call("GET", API + "/me")
    check("no token -> 401 standard error", status == 401 and body.get("code") == "UNAUTHENTICATED", body)

    employee, manager, other, head = (login(w) for w in ("employee", "manager", "employee2", "financehead"))
    status, me = call("GET", API + "/me", employee)
    check("/me", status == 200 and me.get("role") == "EMPLOYEE", me)

    status, claim = call("POST", API + "/claims", employee)
    check("create draft claim", status == 201 and claim.get("status") == "DRAFT", claim)
    cid = claim["claimId"]
    receipt_no = "SMOKE-" + str(int(time.time()))
    status, line = call("POST", f"{API}/claims/{cid}/line-items", employee,
                        {"category": "MEALS", "amount": "300.00", "expenseDate": past_saturday(),
                         "vendor": "Smoke Cafe " + receipt_no, "receiptNumber": receipt_no})
    check("add weekend meal line", status == 201, line)
    status, submitted = call("POST", f"{API}/claims/{cid}/submit", employee)
    check("submit routes weekend claim to manager",
          status == 200 and submitted.get("status") == "PENDING_MANAGER", submitted)

    status, body = call("GET", f"{API}/claims/{cid}", other)
    check("other employee cannot see claim (404)", status == 404, status)
    status, queue = call("GET", API + "/approvals", manager)
    check("claim in manager queue", status == 200 and any(i["claimId"] == cid for i in queue["items"]), queue)
    status, approved = call("POST", f"{API}/claims/{cid}/approve", manager, {})
    check("manager approves", status == 200 and approved.get("status") == "APPROVED", approved)

    status, eligible = call("GET", API + "/payout-batches/eligible-claims", head)
    check("finance head sees payout-eligible claims", status == 200 and eligible["total"] >= 1, eligible)
    status, audit = call("GET", f"{API}/claims/{cid}/audit-events", employee)
    actions = [e["action"] for e in audit.get("items", [])] if isinstance(audit, dict) else []
    check("audit trail recorded", "CLAIM_SUBMITTED" in actions and "CLAIM_APPROVED" in actions, actions)

    status, body = call("GET", PROXY + "/health")
    check("frontend nginx proxies the API", status == 200, f"{status} {body}")
    print("Smoke test passed")


if __name__ == "__main__":
    main()
