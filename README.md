# Expense Tracker

A lightweight personal expense tracker built with **Java 17 and Spring Boot**. Log what you spend and earn, set monthly budgets, and see where your money goes and how much you can still spend today. Each user has their own private data behind a login. It runs in about 230 MB of RAM with no Docker and no database server.

## Features

**Expenses**
- Add, edit, and delete expenses (title, amount, category, date, payment method, note)
- Deleted expenses go to a **Trash** first (with Undo), where you can restore them or delete them forever
- **Repeat** button on each row: adds the same expense again, dated today
- **Quick add**: type `lunch 85 baht yesterday` and the form fills itself in (rule-based, no AI); you check it and save
- **Category pre-selected** while you type a title: the category you used last time for the same title, otherwise keyword rules in English and Thai (`grab ride` → Transport)
- Filter by month and category, **search** title and note, 10 per page
- **Recurring expenses** (rent, phone plan, subscriptions) added automatically each month, with catch-up if the app was off and a database guarantee against duplicates
- **CSV export** of the current filter, and **CSV import** (the app's own format or a bank export), validated before anything is saved

**Insight**
- **Income** and a monthly **balance** (income minus expenses)
- Monthly summary: per-category totals with bars, a **spending-by-day chart** (Chart.js from a CDN), and totals **per payment method** (Cash, Bank transfer, PromptPay, E-wallet, Card)
- Monthly budget per category, with over-budget rows highlighted
- **Daily allowance**: what's left of your budgets divided by the days left in the month, red when you're over
- **Daily average** and an **end-of-month projection** that doesn't count rent and other recurring bills as daily spending
- **Insights** page: each category **compared with last month** (over the same days while a month is running), the **top 5 expenses**, and **spending by weekday**
- **Savings goals** with a progress bar and how much to save each month to reach the target date

**Security**
- Sign up and sign in (Spring Security, BCrypt); every query is scoped to the signed-in user
- Account lockout after 5 wrong passwords in a row (15 minutes, stored in the database)
- Activity log of sign-ins, failed sign-ins, lockouts, and every change, with before/after values

**Everyday polish**
- Amounts shown as ฿1,234.50; currency set in `application.properties`
- Works on phones down to 360px wide (rows turn into cards, every button at least 44px tall) and follows the system dark mode
- Persistent data in a local file database (survives restarts)
- **Backup** button: downloads all your own data as CSV files in a ZIP
- **Restore** from a backup: preview every row first, bad rows are skipped and listed, then add to your data or (after a second confirmation) replace it
- "Today" is always Bangkok time (one `Clock` bean), so a late-night expense lands on the right day and month
- Friendly error pages that never show stack traces or internal messages; a rotating log file in `./logs`

## Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 17 | LTS, light, widely supported |
| Framework | Spring Boot 3.5 (Web, Thymeleaf, Data JPA, Validation, Security) | Covers MVC, DI, beans, and auth |
| Database | H2 in file mode | No server to run, tiny footprint |
| Charts | Chart.js 4 from cdnjs, pinned with an SRI hash | No server memory, no build step |
| Build | Maven (wrapper included) | No global install needed |
| Tests | JUnit 5, MockMvc, DataJpaTest, Spring Security Test | 219 tests |
| CI | GitHub Actions | Runs `./mvnw test` on every push |

Deliberately left out to keep memory low: Actuator, Docker, a front-end framework, and any CSV, chart, or AI library on the server. The only dependencies beyond the Spring Boot starters are H2 and `spring-security-test`.

## Architecture

A classic three-layer Spring MVC app in one process. **Controllers** (`web/`) handle HTTP, bind forms to DTOs and pick a Thymeleaf template; they never touch repositories. **Services** (`service/`) hold the rules (money math, recurring catch-up, import and restore checks, auditing) and take the signed-in user's id on every call. **Repositories** (`repository/`) are Spring Data JPA interfaces whose every query is scoped to that user and leaves out trashed expenses. Pages are rendered on the server, with a little plain JavaScript for quick add, the category hint and the chart. Data lives in an embedded H2 file, and the schema is kept up to date by Hibernate (`ddl-auto=update`), so new columns are nullable and need no migration. Shared rules live in one place each: `Money` (scale 2, HALF_UP), `TimeConfig` (Asia/Bangkok `Clock`), and `SecurityConfig`.

## Project Structure

```
expense-tracker/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/                  Maven wrapper (.mvn/jvm.config caps Maven at 512 MB)
├── run.bat, run.sh                        build the jar if missing, run it with low-memory JVM flags
├── .github/workflows/ci.yml               runs ./mvnw test
├── README.md, CHANGELOG.md, LICENSE
└── src/
    ├── main/
    │   ├── java/com/example/expenses/
    │   │   ├── ExpenseTrackerApplication.java      (@EnableScheduling)
    │   │   ├── config/
    │   │   │   ├── TimeConfig.java                 the Asia/Bangkok Clock
    │   │   │   ├── DemoDataSeeder.java             sample data for the "demo" profile
    │   │   │   ├── DatabaseInUseFailureAnalyzer.java  short message when the database is locked
    │   │   │   └── WebConfig.java
    │   │   ├── model/
    │   │   │   ├── Expense.java, Budget.java, RecurringExpense.java, Income.java, SavingsGoal.java
    │   │   │   ├── Money.java                      scale 2, HALF_UP, totals, division, percentages
    │   │   │   ├── User.java, AuditEntry.java
    │   │   │   ├── Category.java, PaymentMethod.java, AuditAction.java  (enums)
    │   │   │   └── EnumNameConverter.java (+ Category, PaymentMethod and AuditAction converters)
    │   │   ├── repository/
    │   │   │   ├── ExpenseRepository.java          grouped JPQL for totals
    │   │   │   ├── ExpenseSpecifications.java      optional filters, always scoped to the owner
    │   │   │   └── Income, Budget, RecurringExpense, SavingsGoal, User and AuditEntry repositories
    │   │   ├── security/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── AppUserDetails.java, AppUserDetailsService.java
    │   │   │   └── LoginAttemptListener.java, LoginAttemptService.java   (lockout)
    │   │   ├── service/
    │   │   │   ├── ExpenseService, IncomeService, BudgetService, SummaryService, InsightsService,
    │   │   │   │   SavingsGoalService, UserService, AuditService
    │   │   │   ├── BackupService.java              per-user ZIP of CSV files
    │   │   │   ├── RestoreService.java             preview and apply a backup, skipping bad rows
    │   │   │   ├── RecurringExpenseService.java, RecurringExpenseScheduler.java
    │   │   │   ├── CsvExportService.java, CsvImportService.java, Csv.java
    │   │   │   ├── CategorySuggester.java, QuickEntryParser.java
    │   │   │   └── NotFoundException (+ Expense and RecurringExpense variants), UsernameTakenException
    │   │   ├── web/
    │   │   │   ├── ExpenseController, TrashController, IncomeController, SummaryController
    │   │   │   ├── RecurringController, ImportController, BackupController, RestoreController,
    │   │   │   │   InsightsController, GoalsController
    │   │   │   ├── AuthController, ActivityController
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── MoneyFormatter.java ("@money" in templates), CurrentUserInterceptor.java
    │   │   └── dto/
    │   │       ├── ExpenseForm, IncomeForm, BudgetForm, RecurringForm, RegistrationForm
    │   │       ├── ExpenseFilter, CategoryTotal, CategorySummary, MonthlySummary, MonthlyBalance
    │   │       └── DailyTotal, DailySpending, DailyAllowance, PaymentMethodTotal, SpendingPace,
    │   │           Change, CategoryComparison, MonthComparison, WeekdaySpending, GoalForm, GoalMoneyForm
    │   └── resources/
    │       ├── application.properties, application-dev.properties, application-demo.properties
    │       ├── messages.properties
    │       ├── static/css/style.css
    │       ├── static/js/app.js, static/js/summary-chart.js
    │       └── templates/
    │           ├── layout.html, error.html, summary.html, insights.html, income.html, recurring.html
    │           ├── trash.html, goals.html, activity.html, fragments/change.html
    │           ├── error/404.html, error/500.html
    │           ├── restore/upload.html, preview.html, report.html
    │           ├── auth/login.html, auth/register.html
    │           └── expenses/list.html, form.html, import.html, not-found.html
    └── test/
        ├── java/com/example/expenses/   (26 test classes, see Testing)
        ├── resources/config/            turns on the "test" profile
        └── resources/application-test.properties   in-memory database, no log file
```

## Data Model

**Expense**

| Field | Type | Rules |
|---|---|---|
| id | Long | auto-generated |
| owner | User | set from the signed-in user, never from the form |
| title | String | required, max 100 chars |
| amount | BigDecimal | required, greater than 0, at most 2 decimal places |
| category | Category (enum) | required: FOOD, TRANSPORT, RENT, STUDY, HEALTH, FUN, OTHER |
| date | LocalDate | required, not in the future |
| note | String | optional, max 255 chars |
| paymentMethod | PaymentMethod (enum) | optional: CASH, BANK, PROMPTPAY, EWALLET, CARD; new expenses default to Cash, older ones show "Not set" |
| deletedAt | Instant | set while the expense is in the trash; trashed expenses are left out of every list and total |
| recurringExpenseId | Long | set when created by a recurring expense; unique together with the date, so an occurrence can't be created twice |

**Budget**: `owner`, `category` (unique per user), `monthlyLimit` (BigDecimal)

**Income**: `owner`, `amount`, `source`, `date`

**SavingsGoal**: `owner`, `name`, `targetAmount`, `savedAmount`, `targetDate` (optional)

**RecurringExpense**: `owner`, `title`, `amount`, `category`, `note`, `paymentMethod`, `dayOfMonth`, `nextDueDate`, `active`

**User**: `username` (unique, case-insensitive), `passwordHash` (BCrypt), `failedAttempts`, `lockedUntil`, `createdAt`

**AuditEntry**: `userId`, `action`, `details`, `createdAt` (append-only)

Money is always `BigDecimal`, never `double`, and every calculated amount goes through `Money`: 2 decimal places, rounded HALF_UP (percentages to 1 place). Expenses have indexes on (owner, date), date, category, and (date, category). Enums are stored as plain `varchar` through converters, so adding a value later needs no schema change.

## Routes

Everything except `/login` and `/register` requires signing in.

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Redirect to `/expenses` |
| GET | `/expenses?month=2026-10&category=FOOD&q=tea&page=0` | List with filters and search |
| GET | `/expenses/new` | New expense form (`?quick=lunch+85+yesterday` pre-fills it) |
| POST | `/expenses` | Create |
| GET | `/expenses/{id}/edit` | Edit form |
| POST | `/expenses/{id}` | Update |
| POST | `/expenses/{id}/delete` | Move to trash |
| POST | `/expenses/{id}/repeat` | Add a copy dated today |
| GET | `/trash` | Trash |
| POST | `/trash/{id}/restore`, `/trash/{id}/delete` | Restore, or delete forever |
| GET | `/expenses/export?month=2026-10` | Download CSV (also takes `category` and `q`) |
| GET, POST | `/expenses/import` | Import form, upload CSV |
| GET | `/expenses/suggest-category?title=grab+ride` | JSON category guess (used while typing) |
| GET | `/summary?month=2026-10` | Balance, daily allowance, totals by category and payment method, chart, budgets |
| GET, POST | `/income?month=2026-10` | List and add income |
| POST | `/income/{id}/delete` | Delete income |
| POST | `/budgets`, `/budgets/{category}/delete` | Set or remove a budget |
| GET, POST | `/recurring` | List and add recurring expenses |
| POST | `/recurring/{id}/pause`, `/resume`, `/delete` | Manage a recurring expense |
| GET | `/insights?month=2026-10` | Month-over-month comparison, daily average and projection, top 5, weekdays |
| GET, POST | `/goals` | Savings goals: list and add |
| POST | `/goals/{id}/deposit`, `/withdraw`, `/delete` | Add money, take money out, delete a goal |
| GET | `/activity` | Your activity log |
| GET | `/backup` | Download your data as a ZIP of CSV files |
| GET, POST | `/restore` | Upload a backup ZIP or expenses CSV and preview it |
| POST | `/restore/confirm` | Add the valid rows, or replace your expenses and budgets (`mode=replace` needs `confirmReplace`) |
| GET, POST | `/login`, `/register` | Sign in, create an account |
| POST | `/logout` | Sign out |

## Run It

Requirements: JDK 17+ (Maven is not needed, the wrapper is included). The commands work as written in PowerShell, Git Bash, macOS and Linux (in the old cmd.exe, type `mvnw` instead of `./mvnw`).

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080 and create an account. Data is stored in `./data/expenses.mv.db`, and the log in `./logs/app.log` (2 MB per file, 14 days, 8 MB in total; it never contains amounts, notes or passwords).

Or use the run script, which builds the jar the first time and starts it with the low-memory settings below:

```bash
./run.sh
```

On Windows, double-click `run.bat` or type `.\run.bat` (it uses the same flags). The script only builds when the jar is missing, so after changing the code rebuild with `./mvnw -DskipTests package`. Anything after the script name is passed to the app, for example `./run.sh --server.port=9090`.

### Profiles

| Profile | How | What changes |
|---|---|---|
| (default) | `./mvnw spring-boot:run` or `run.bat` | Your data in `./data/expenses`, H2 console off |
| `dev` | `./mvnw spring-boot:run -Pdev` | H2 console at http://localhost:8080/h2-console (sign in to the app first; JDBC URL `jdbc:h2:file:./data/expenses`, user `sa`, empty password), Thymeleaf templates not cached |
| `demo` | `./mvnw spring-boot:run -Pdemo` | Sample data in its own file, `./data/demo` |
| `test` | turned on automatically by `./mvnw test` | In-memory database, no log file |

No secrets are stored in the properties files. If you give the database a password, set it in the `DB_PASSWORD` environment variable.

### Backups

- **Your own data**: the **Backup** link next to Sign out downloads a ZIP with `expenses.csv` (trash included, marked in the `deleted_at` column), `income.csv`, `budgets.csv`, `recurring.csv` and `goals.csv`. Bring it back with **Restore** (next to Backup): upload the ZIP (or just `expenses.csv`), check the preview, which lists every row it will skip and why, then choose **Add** (the default: keeps what you have and adds the backup's expenses and any missing budgets) or **Replace** (deletes your current expenses and budgets first, and asks you to tick a confirmation box). Afterwards a report shows how many rows were added and skipped.
- **The whole database** (every account), for whoever runs the app: stop the app and copy `data/expenses.mv.db`. Or write it out as a SQL script with H2's own tool (Maven has already downloaded it):

```bash
java -cp ~/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar org.h2.tools.Script -url jdbc:h2:file:./data/expenses -user sa -script backup.sql
```

The web app deliberately has no whole-database download: it would hand any signed-in user everyone's data and password hashes.

### Try it with sample data

The `demo` profile creates a `demo` account (password `demo1234`) with about 26 sample expenses and 3 budgets. It uses its own file, `./data/demo.mv.db`, so it never touches your real data.

```bash
./mvnw spring-boot:run -Pdemo
```

`-Pdemo` turns on a Maven profile that sets the Spring profile, and it works the same in PowerShell, cmd and bash. (The long form `-Dspring-boot.run.profiles=demo` breaks in PowerShell, which splits it at the first dot.)

### Low-memory run

```bash
./mvnw clean package -DskipTests
java -Xmx256m -Xms64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar target/expense-tracker-0.0.1-SNAPSHOT.jar
```

Measured on Windows with the demo data, after loading every page three times: about **230 MB** of process memory, and a startup time of about 5 seconds.

- `-Xmx256m` caps the heap
- `-XX:+UseSerialGC` uses the lightest garbage collector
- `-XX:TieredStopAtLevel=1` trades peak speed for lower memory and faster startup

Add `--spring.profiles.active=demo` to the `java` command for the sample data. `run.bat` and `run.sh` run exactly this command.

### Key configuration (`application.properties`)

```properties
spring.datasource.url=jdbc:h2:file:./data/expenses;AUTO_SERVER=FALSE
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
spring.main.lazy-initialization=true
server.tomcat.threads.max=20
spring.datasource.hikari.maximum-pool-size=5

app.currency.code=THB
# .properties files are read as ISO-8859-1, so the baht sign is written as an escape
app.currency.symbol=\u0E3F
app.security.max-failed-logins=5
app.security.lockout-duration=15m
spring.servlet.multipart.max-file-size=1MB
```

The recurring-expense job runs at 00:05 every day; change it with `app.recurring.cron`.

## Testing

```bash
./mvnw test
```

Tests use a fresh in-memory database per test context, so they never touch `./data`.

| Test class | What it covers |
|---|---|
| `ExpenseRepositoryTest` | Month, category and search filters, wildcard escaping, pagination, grouped totals, owner scoping |
| `ExpenseControllerTest` | Validation errors keep input, not-found page, bad parameters, pagination links, CSV download, quick add, suggestions |
| `SummaryServiceTest` | Summary math (no expenses, exactly at the limit, over the limit), balance, payment-method shares, and the daily allowance (mid-month, last day, overspend, rounding, future and past months) |
| `SummaryControllerTest` | Summary page, over-budget highlight, chart data and table view, budget form |
| `InsightsServiceTest` | Month-over-month math (full months, same days while a month runs, shorter previous month, new and dropped categories, rounding), projection math (mid-month, first and last day, recurring left out of the pace, upcoming recurring added, finished months), weekday counts and averages |
| `SavingsGoalTest`, `GoalsTest` | Progress and monthly-amount math, deposits, no over-withdrawal, validation, other users can't see or change your goals |
| `TrashTest` | Delete moves to trash and hides it everywhere; restore and Undo bring it back; delete forever; other users can't touch your trash |
| `BackupTest` | The ZIP has your four CSV files, includes the trash, and has no other user's data or password hashes |
| `SecurityIntegrationTest` | User A cannot list, open, edit, delete or export user B's expenses; CSRF; login; lockout; registration; audit log; import; recurring |
| `RecurringExpenseServiceTest` | Due dates, day 31 in short months, catch-up, running twice, a restart with stale state, the unique constraint, pause/resume, per-user isolation |
| `CsvExportServiceTest`, `CsvImportServiceTest` | Quoting, CSV injection, parsing, row errors, export then import round trip |
| `QuickEntryParserTest`, `CategorySuggesterTest` | "lunch 85 baht yesterday", dates, weekdays, Thai words, keyword rules |
| `BackupRestoreTest` | Backup then wipe then restore gives back identical data; add mode keeps existing data and budgets; bad rows skipped and reported; replace needs the confirmation tick and leaves other users' data alone; confirming without a preview asks for the file again |
| `MoneyTest` | Scale 2 and HALF_UP everywhere, totals, division by zero, percentages |
| `TimeRulesTest` | "Today" follows Bangkok, not UTC: just after midnight, Oct 31 to Nov 1, Feb 29 |
| `ProfilesTest` | Tests run on an in-memory database, the H2 console is off by default and on in `dev`, no password in the config files |
| `ErrorPagesTest`, `LoggingPrivacyTest` | Friendly 404 and 500 pages with no stack trace or message; logs never contain amounts, notes or passwords |
| `UserLockoutTest`, `MoneyFormatterTest`, `DemoDataSeederTest`, `DatabaseInUseFailureAnalyzerTest`, `ExpenseTrackerApplicationTests` | Lockout math, ฿ formatting, demo seed, locked-database message, context starts |

## Security Notes

The threats considered, and how the project handles each:

| Threat | How it's handled |
|---|---|
| **SQL injection** | All queries go through Spring Data: derived queries, JPQL with named parameters, and Criteria `Specification`s. No SQL is built from strings. In search, `%` and `_` typed by the user are escaped and matched literally. |
| **XSS** | Thymeleaf `th:text` escapes all output and the templates never use `th:utext`. Chart data is passed in `data-*` attributes and read with `getAttribute`. |
| **Broken access control** | Every lookup includes the owner id (`findByIdAndOwnerId`, the `ownedBy` specification, `e.owner.id = :ownerId` in JPQL). Another user's expense id returns 404, exactly like a missing one, so ids can't be probed. `SecurityIntegrationTest` proves this for list, edit, update, delete, export and summary. |
| **CSRF** | Spring Security's CSRF token is on every POST form (Thymeleaf adds it automatically), including sign-out. A POST without it gets 403 (tested). The session cookie is `HttpOnly` and `SameSite=Lax`, and never put in URLs. |
| **Password guessing** | 5 wrong passwords in a row lock the account for 15 minutes; the count is stored in the database. Wrong username and wrong password show the same message. |
| **Password storage** | BCrypt hashes only. Passwords must be 8 to 72 characters (BCrypt ignores anything past 72 bytes). The hash is erased from the session after sign-in. |
| **Session fixation** | Spring Security issues a new session id at sign-in. |
| **Mass assignment** | Forms bind to DTOs (`ExpenseForm` etc.), never to entities. Ids and owners come from the URL and the session. |
| **CSV injection** | Exported cells that start with `=`, `+`, `-` or `@` get a `'` prefix so spreadsheets don't run them as formulas; import removes it again. |
| **Backups** | The Backup download only contains the signed-in user's rows, never password hashes or other users' data (tested). |
| **Uploads** | Uploads are limited to 1 MB. CSV import allows 2,000 rows, is read as text only, and saves nothing unless every row is valid. Restore unzips at most 5 MB per file (no zip bombs) and only reads the CSV files it knows. |
| **Error details** | Error pages and JSON errors never include stack traces, exception names or messages. The log never contains amounts, notes or passwords. |
| **H2 console** | Off by default. With `-Pdev` it is on, but only for signed-in users. |
| **Third-party code** | Chart.js is pinned to one version with a Subresource Integrity hash, so a tampered file is refused. Only two dependencies beyond the Spring Boot starters. |
| **Accountability** | The activity log records sign-ins, failed sign-ins, lockouts, and every create/edit/delete with before and after values. |

Security headers are Spring Security's defaults: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, and `X-XSS-Protection: 0` (HSTS is added only over HTTPS).

Known gaps, as next steps:
- No HTTPS when run locally (put it behind a reverse proxy for real use).
- No Content-Security-Policy header yet.
- Lockout can be used to lock someone else out for 15 minutes; IP-based rate limiting would reduce that.
- No password reset, no two-factor authentication.
- The H2 file is not encrypted at rest.

## Build Plan

| Phase | Goal | Status |
|---|---|---|
| 1 | Project setup, entity, repository, CRUD pages | Done |
| 2 | Validation, error handling, month/category filter, pagination | Done |
| 3 | Summary page, budgets, over-budget warnings | Done |
| 4 | CSV export, tests, styling, demo data | Done |
| 5 | Login with Spring Security, per-user expenses | Done |
| Extras | Currency formatting, search, recurring expenses, auto-categorize, Chart.js chart, account lockout, audit log, CSV import, quick add, dark mode, CI | Done |
| Usability | Income and balance, daily allowance, category from history, Repeat, duplicate-safe recurring, trash, payment methods, backup | Done |
| Insights | Month-over-month comparison, daily average and projection, top 5 expenses, spending by weekday, savings goals | Done |
| Foundations | Git hygiene, Bangkok clock, profiles, money rules, indexes, restore, error pages and logging, 360px mobile, run scripts and docs | Done |

## Roadmap Ideas

- Tags, receipt photos (stored on disk), splitting expenses with friends
- Anomaly alerts (an expense far above your normal for that category)
- Encrypted backup export, two-factor authentication (TOTP)
- An LLM-written monthly summary that sends only totals, never notes
- REST API with OpenAPI docs
- Switch H2 to PostgreSQL through config only
- Screenshots and a short demo GIF in this README

## What This Project Demonstrates

- Spring MVC, dependency injection (constructor injection everywhere), and bean configuration
- JPA entities, derived queries, JPQL constructor expressions, Criteria specifications, pagination
- Bean Validation with server-side error display
- Spring Security: form login, BCrypt, CSRF, per-user authorization, authentication events
- Scheduling (`@Scheduled`) with lazy initialization
- Layered architecture (controller, service, repository)
- Unit, slice and integration testing, plus CI
- Resource-conscious deployment on small machines

## Known limitations

- **One machine, one process.** H2 in file mode allows one app at a time; a second copy (or an IDE run left open) fails with "database already in use".
- **No real migrations.** The schema is updated by Hibernate's `ddl-auto=update`, which adds tables, columns and indexes but never changes or removes them. Renaming a column or making one NOT NULL would need a manual step.
- **Single currency and time zone.** Amounts have no currency of their own (the symbol is a setting) and "today" is always Asia/Bangkok.
- **Restore covers expenses and budgets only.** Income, recurring expenses and savings goals are in the backup ZIP but are not restored yet. Restore only adds or replaces; it doesn't merge edits to the same expense.
- **Not built for the internet as is.** No HTTPS, no Content-Security-Policy, no password reset or two-factor sign-in, and the database file is not encrypted (see Security Notes).
- **The chart needs internet.** Chart.js comes from a CDN; offline, the summary shows a short note and the same numbers as a table.
- **Small data.** Lists and totals are computed per request with no caching, which is fine for one person's years of expenses but not for thousands of users.
- **Tested in Chrome**, on desktop and at 360px wide; other browsers should work but weren't checked.

## License

MIT, see [LICENSE](LICENSE).
