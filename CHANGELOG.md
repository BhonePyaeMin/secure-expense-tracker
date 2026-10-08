# Changelog

All notable changes to this project. Dates are in Asia/Bangkok time.

## 2026-10-09 — Foundations

### Added
- `run.bat` and `run.sh`: build the jar if it's missing, then run it with `-Xmx256m -Xms64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`.
- **Restore** from a backup ZIP or an expenses CSV: preview every row, skip bad rows with the reason, choose Add (default) or Replace (needs a confirmation tick), then see a report.
- Friendly 404 and 500 pages in the normal layout.
- Log file at `./logs/app.log`, rolled at 2 MB, kept 14 days, 8 MB in total.
- `dev` profile (`./mvnw spring-boot:run -Pdev`) with the H2 console, and a `test` profile used by `./mvnw test`.
- Indexes on expense date, category, and date + category.
- A short message instead of a stack trace when the database is already in use.
- LICENSE (MIT), this changelog, and Architecture and Known limitations sections in the README.

### Changed
- "Today" comes from one `Clock` set to Asia/Bangkok, everywhere (dates, months, the scheduler, audit times, validation).
- All money math goes through one `Money` helper: 2 decimal places, HALF_UP. The daily allowance (was rounded down) and a goal's monthly amount (was rounded up) now round HALF_UP too, so they can differ by 0.01.
- The H2 console is off unless the `dev` profile is on. The database password is read from `DB_PASSWORD` instead of a file.
- Phones down to 360px: no sideways scrolling, buttons and links at least 44px tall, header and actions wrap instead of overflowing.

### Security
- Error pages and JSON errors never include stack traces, exception names or messages.
- Logs never contain amounts, notes or passwords; the demo seeder no longer logs its password.
- `.gitignore` covers logs, `.env` and IDE folders.

## 2026-10-08 — First version

- Expenses with validation, filters, search and pagination; monthly summary with budgets and a chart; CSV export and import; quick add; recurring expenses; trash; payment methods.
- Income and balance, daily allowance, Insights (month-over-month, projection, top 5, weekdays) and savings goals.
- Sign-in with BCrypt, per-user data, account lockout, and an activity log.
- Per-user backup ZIP, demo data (`-Pdemo`), dark mode, and CI on GitHub Actions.
