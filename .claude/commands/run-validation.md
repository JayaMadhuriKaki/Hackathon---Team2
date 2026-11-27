---
description: Run all tests, record results, log defects and update the validation deliverables
argument-hint: "[optional scope: frontend | backend | integration | all]"
---

Use the **quality-engineering** skill.

1. Make sure `deliverables/test-cases.xlsx` has cases for every acceptance criterion; add missing ones.
2. Ensure test data exists in `tests/test-data/`.
3. Run the suites in scope (default: all) and capture results.
4. Record Pass/Fail, date and notes per case in `test-cases.xlsx`; add a `BUG-###` row in the Defects sheet for each failure.
5. Update `deliverables/validation-matrix.xlsx` and the "Test Case" / "Status" columns in the traceability matrix.

Scope: $ARGUMENTS

Finish with: pass/fail counts, Must-have requirements not yet validated, and open Critical/High defects.
