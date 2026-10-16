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

Salary Slip Maker is a separate Kotlin/Jetpack Compose + Room application with its own package, payroll domain, and database (`salary_slip_maker.db`). It targets Android 14 (API 34), supports Android 7.0+ (API 24), and requests no `INTERNET` permission. It uses decimal arithmetic for salary values and explicit Room migrations; no Money Manager records or schema are shared.

The MVP implementation is complete: business and employee profiles, reusable templates, draft editing, immutable issued history, correction/duplication links, A4 PDF creation/export/share/print, and validated local JSON backup/restore.

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

- [x] Phase 1 — inspect the base app; define payroll models; local database; business and employee profiles.
- [x] Phase 2 — earnings/deductions, totals, draft editing, reusable templates.
- [x] Phase 3 — issuance, immutable history, search/filter, duplication, corrected replacements.
- [x] Phase 4 — A4 PDF export, native sharing/printing, calculation parity checks.
- [x] Phase 5 — validated local backup/restore, offline/privacy checks, migration and release preparation.

## Privacy and limitations

- All application data is stored in the local Room database. The app does not require an account or network access.
- Backup files are unencrypted JSON and may contain employee details and salary amounts. Choose a private destination; restoration replaces all local records only after an explicit confirmation.
- PDFs are generated locally. Exporting, printing, or sharing occurs only after the user selects a system destination or recipient.
- The app does not calculate tax, BPJS, attendance, or other statutory payroll amounts. Employers enter deductions manually and must verify applicable rules.
- Keep the release keystore and `keystore.properties` backed up securely; they are intentionally excluded from Git.

## Build and test

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
./gradlew :app:assembleRelease --no-daemon
```

Requirements: JDK 17 and Android SDK 34. The release variant requires a local `keystore.properties` file and the matching `release.keystore`.

Full requirements: [`docs/SALARY_SLIP_MAKER_BRIEF.txt`](docs/SALARY_SLIP_MAKER_BRIEF.txt).
