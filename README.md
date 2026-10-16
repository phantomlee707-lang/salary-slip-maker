# Salary Slip Maker

**Rainweaver Studio · Android · Local-first / offline-first**

A lightweight Android app for small businesses and independent employers to prepare professional salary slips without a full payroll or HR platform. The app is designed around editable drafts, transparent arithmetic, immutable issued records, and on-device PDF generation.

> This is not a statutory payroll calculator. Tax, BPJS, attendance, leave, scheduling, disbursement, and HRIS workflows are explicitly out of MVP scope. Any deduction amounts are entered and reviewed by the employer.

## MVP

- Reusable business profile and employee records, including employee archiving.
- Draft payslips with payroll period, employee, payment date, currency, notes, and custom earning/deduction lines.
- Recalculated gross earnings, total deductions, and net payable.
- Reusable salary templates that copy into drafts, never rewrite issued slips.
- Issued-history search/filter, duplication to a new draft, and corrected replacements linked to the original.
- Print-friendly A4 PDF, native Android share/print, and local backup/restore.
- No required account, subscription, remote service, or network connection.

## Development baseline

Phase 1 begins by adapting proven patterns from the separate [Money Manager](https://github.com/phantomlee707-lang/money-manager) Android project. Its stack is Kotlin, Jetpack Compose, Room, and Android's document APIs; its app uses `minSdk 24`, `targetSdk 34`, and requests no `INTERNET` permission. Salary Slip Maker will have a distinct application ID, database, and payroll domain model. No Money Manager transaction schema or financial records are shared.

## Data-integrity principles

1. Recompute totals from line items; never treat a displayed total as the source of truth.
2. Keep preview, PDF, and stored draft values derived from the same calculation path.
3. Issuance freezes the displayed business/employee details and all amounts. Profile/template edits must not rewrite history.
4. Corrections create a replacement linked to the issued original; they do not overwrite it.
5. Validate monetary inputs and dates before saving or issuing.
6. Use explicit, data-preserving Room migrations before any release with user data; do not silently drop payroll records.
7. Backup restore must validate input and explain replace/merge behavior before modifying local records.
8. Never imply app lock is encryption or statutory/tax compliance.

## Roadmap

- [ ] Phase 1 — inspect Money Manager patterns; define payroll models; local database; business and employee profiles.
- [ ] Phase 2 — earnings/deductions, totals, draft editing, reusable templates.
- [ ] Phase 3 — issuance, immutable history, search/filter, duplication, corrected replacements.
- [ ] Phase 4 — A4 PDF layout, sharing/printing, rendering tests.
- [ ] Phase 5 — validated backup/restore, offline/privacy checks, migration tests, release preparation.

Full requirements: [`docs/SALARY_SLIP_MAKER_BRIEF.txt`](docs/SALARY_SLIP_MAKER_BRIEF.txt).
