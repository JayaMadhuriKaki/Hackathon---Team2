"""Small HTTP client and data helpers for the ExpenseFlow pytest suites (CLAUDE.md §16.3, ASM-012).

The suites are black-box: they talk to the running Spring Boot API only. They run against the
Docker Compose stack seeded with the 8 synthetic demo users (backend DemoUserSeeder).

Re-runs on the same database must not trip rules that look at earlier claims:
  * POL-001 sums meals per claimant per date across claims  -> every claim gets fresh dates
  * POL-006 vendor + amount + date                          -> every line gets a unique vendor
  * POL-007 receipt file SHA-256                            -> every receipt has unique bytes
  * POL-009 date + receipt number                           -> every line gets a unique receipt no.
"""

from __future__ import annotations

import datetime as dt
import os
import random
import uuid
from dataclasses import dataclass, field
from decimal import Decimal

import httpx

BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080/api/v1").rstrip("/")
# Same variable the stack uses to seed demo logins. The fallback is the docker-compose.yml default
# for the local, synthetic-data stack only; override it in .env and here for anything shared.
PASSWORD = os.environ.get("SEED_DEMO_PASSWORD", "Demo-Only-2026!")

# Demo personas (backend/src/main/java/.../config/DemoUserSeeder.java)
EMPLOYEE = "employee"  # manager = manager, Consulting
EMPLOYEE2 = "employee2"  # manager = manager, Consulting
MANAGER = "manager"  # EMPLOYEE role, manager = financehead, Consulting
AUDITOR = "auditor"  # FINANCE_AUDITOR, manager = financehead
AUDITOR2 = "auditor2"  # FINANCE_AUDITOR, manager = financehead
FIN_HEAD = "financehead"  # FINANCE_HEAD (+ admin), no manager
NO_MANAGER = "nomanager"  # EMPLOYEE, no manager, Operations
STAFF = "staff"  # EMPLOYEE, manager = auditor, Finance

AUTO_APPROVED_STATES = {"APPROVED", "SAMPLE_AUDIT_PENDING"}
ERROR_KEYS = {"code", "message", "details", "traceId"}

RUN_ID = uuid.uuid4().hex[:8].upper()

# ---------------------------------------------------------------- unique test data


class DateAllocator:
    """Hands out past dates that no other claim in this run (and very likely no earlier run) uses.

    Each allocation reserves a 28-day window so multi-night hotel stays never overlap.
    """

    def __init__(self) -> None:
        first = dt.date(1995, 1, 2)
        windows = (dt.date(2025, 6, 30) - first).days // 28
        self._rng = random.Random()
        self._first = first
        self._free = list(range(windows))
        self._rng.shuffle(self._free)

    def window(self) -> dt.date:
        return self._first + dt.timedelta(days=28 * self._free.pop())

    def weekday(self) -> dt.date:
        """A Tuesday (never a weekend, never next to one)."""
        d = self.window()
        return d + dt.timedelta(days=(1 - d.weekday()) % 7)

    def saturday(self) -> dt.date:
        d = self.window()
        return d + dt.timedelta(days=(5 - d.weekday()) % 7)


DATES = DateAllocator()
_seq = 0


def uniq(prefix: str) -> str:
    global _seq
    _seq += 1
    return f"{prefix}-{RUN_ID}-{_seq}"


def receipt_bytes(kind: str = "pdf") -> bytes:
    """Minimal synthetic receipt with unique content (unique SHA-256)."""
    tag = uuid.uuid4().hex.encode()
    if kind == "png":
        png = bytes.fromhex(
            "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
            "1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082"
        )
        return png + b"tEXt" + tag
    return b"%PDF-1.4\n% synthetic receipt " + tag + b"\n%%EOF"


def money(value) -> Decimal:
    return Decimal(str(value))


# ---------------------------------------------------------------- client


@dataclass
class Session:
    who: str
    token: str
    user: dict = field(default_factory=dict)

    @property
    def headers(self) -> dict:
        return {"Authorization": f"Bearer {self.token}"}


class Api:
    """Thin wrapper over httpx that logs in once per persona."""

    def __init__(self, base_url: str = BASE_URL) -> None:
        self.base = base_url
        self.http = httpx.Client(base_url=base_url, timeout=30.0)
        self._sessions: dict[str, Session] = {}

    def close(self) -> None:
        self.http.close()

    # ---- raw calls
    def login(self, who: str, password: str = PASSWORD) -> httpx.Response:
        return self.http.post("/auth/login", json={"email": f"{who}@expenseflow.test", "password": password})

    def session(self, who: str) -> Session:
        if who not in self._sessions:
            r = self.login(who)
            assert r.status_code == 200, f"login for {who} failed: {r.status_code} {r.text}"
            body = r.json()
            self._sessions[who] = Session(who, body["accessToken"], body.get("user") or {})
        return self._sessions[who]

    def request(self, who: str | None, method: str, path: str, **kw) -> httpx.Response:
        headers = dict(kw.pop("headers", {}) or {})
        if who is not None:
            headers.update(self.session(who).headers)
        return self.http.request(method, path, headers=headers, **kw)

    def get(self, who, path, **kw):
        return self.request(who, "GET", path, **kw)

    def post(self, who, path, **kw):
        return self.request(who, "POST", path, **kw)

    def put(self, who, path, **kw):
        return self.request(who, "PUT", path, **kw)

    def delete(self, who, path, **kw):
        return self.request(who, "DELETE", path, **kw)

    def user_id(self, who: str) -> str:
        me = self.get(who, "/me")
        assert me.status_code == 200, me.text
        return me.json()["userId"]

    # ---- claim helpers
    def new_claim(self, who: str) -> str:
        r = self.post(who, "/claims")
        assert r.status_code == 201, r.text
        return r.json()["claimId"]

    def add_line(self, who: str, claim_id: str, category: str, amount, date: dt.date, *, vendor: str | None = None,
                 receipt_no: str | None = None, city: str | None = None, nights: int | None = None,
                 expect: int = 201) -> httpx.Response:
        body = {
            "category": category,
            "amount": str(amount),
            "expenseDate": date.isoformat(),
            "vendor": vendor or uniq("TST Vendor"),
            "receiptNumber": receipt_no or uniq("TST-R"),
        }
        if category == "HOTEL":
            body["city"] = city or "Pune"
            body["checkInDate"] = date.isoformat()
            body["checkOutDate"] = (date + dt.timedelta(days=nights or 1)).isoformat()
        r = self.post(who, f"/claims/{claim_id}/line-items", json=body)
        if expect is not None:
            assert r.status_code == expect, f"add_line {body} -> {r.status_code} {r.text}"
        return r

    def upload(self, who: str, claim_id: str, line_id: str, content: bytes | None = None,
               name: str = "receipt.pdf", content_type: str = "application/pdf") -> httpx.Response:
        files = {"file": (name, content if content is not None else receipt_bytes(), content_type)}
        return self.post(who, f"/claims/{claim_id}/line-items/{line_id}/receipt", files=files)

    def line(self, who: str, claim_id: str, category: str, amount, date: dt.date, *, receipt: bool | bytes = True,
             **kw) -> str:
        """Adds a line and, by default, a unique receipt. Returns the line id."""
        line_id = self.add_line(who, claim_id, category, amount, date, **kw).json()["lineItemId"]
        if receipt:
            content = receipt if isinstance(receipt, bytes) else None
            up = self.upload(who, claim_id, line_id, content)
            assert up.status_code == 201, up.text
        return line_id

    def submit(self, who: str, claim_id: str, key: str | None = None) -> httpx.Response:
        headers = {"Idempotency-Key": key} if key else None
        return self.post(who, f"/claims/{claim_id}/submit", headers=headers)

    def claim(self, who: str, claim_id: str) -> dict:
        r = self.get(who, f"/claims/{claim_id}")
        assert r.status_code == 200, r.text
        return r.json()

    def decide(self, who: str, claim_id: str, action: str, comment: str | None = None,
               key: str | None = None) -> httpx.Response:
        headers = {"Idempotency-Key": key} if key else None
        body = {} if comment is None else {"comment": comment}
        return self.post(who, f"/claims/{claim_id}/{action}", json=body, headers=headers)

    def submitted_claim(self, who: str, lines: list[tuple], expect_status: int = 200) -> dict:
        """lines: (category, amount, date[, kwargs]). Returns the claim detail after submit."""
        claim_id = self.new_claim(who)
        for spec in lines:
            category, amount, date, *rest = spec
            kw = rest[0] if rest else {}
            self.line(who, claim_id, category, amount, date, **kw)
        r = self.submit(who, claim_id)
        assert r.status_code == expect_status, r.text
        return r.json()

    def claim_of_total(self, who: str, total: str, *, weekend: bool = False) -> dict:
        """A clean claim (no breach, no duplicate) whose total is exactly `total`, via one hotel line in a
        tier-1 city with enough nights to stay within 6,000/night. Weekday check-in unless `weekend`."""
        amount = money(total)
        nights = max(1, int((amount / 6000).to_integral_value(rounding="ROUND_CEILING")))
        date = DATES.saturday() if weekend else DATES.weekday()
        return self.submitted_claim(who, [("HOTEL", amount, date, {"city": "Mumbai", "nights": nights})])

    def all_items(self, who: str, path: str, params: dict | None = None, max_pages: int = 100) -> list[dict]:
        """Walks every page of a list endpoint ({items, page, pageSize, total})."""
        items: list[dict] = []
        page = 1
        while page <= max_pages:
            r = self.get(who, path, params={**(params or {}), "page": page, "pageSize": 100})
            assert r.status_code == 200, r.text
            body = r.json()
            items.extend(body["items"])
            if not body["items"] or len(items) >= body["total"]:
                break
            page += 1
        return items

    @staticmethod
    def current_steps(claim: dict) -> list[str]:
        sub = claim["submissionNo"]
        return [f"{s['stepType']}:{s['status']}" for s in claim["steps"] if s["submissionNo"] == sub]


def assert_error(r: httpx.Response, status: int, code: str | None = None) -> dict:
    assert r.status_code == status, f"expected {status}, got {r.status_code}: {r.text}"
    body = r.json()
    assert ERROR_KEYS <= set(body), f"error body must have {ERROR_KEYS}: {body}"
    assert "at com.hackathon" not in r.text and "java.lang." not in r.text, "no stack traces in errors"
    if code:
        assert body["code"] == code, body
    return body
