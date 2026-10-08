# Expense Tracker

A lightweight personal expense tracker built with **Java 17 and Spring Boot**. Log what you spend, filter and search, set monthly budgets, and see where your money goes. Each user has their own private data behind a login. It runs in about 230 MB of RAM with no Docker and no database server.

## Features

**Expenses**
- Add, edit, and delete expenses (title, amount, category, date, note), with a confirmation before deleting
- **Quick add**: type `lunch 85 baht yesterday` and the form fills itself in (rule-based, no AI); you check it and save
- **Category suggestions** while you type a title (`grab ride` → Transport), from keyword rules in English and Thai
- Filter by month and category, **search** title and note, 10 per page
- **Recurring expenses** (rent, phone plan, subscriptions) added automatically each month, with catch-up if the app was off
- **CSV export** of the current filter, and **CSV import** (the app's own format or a bank export), validated before anything is saved

**Insight**
- Monthly summary: total spent, per-category totals with bars, and a **spending-by-day chart** (Chart.js from a CDN)
- Monthly budget per category, with over-budget rows highlighted

**Security**
- Sign up and sign in (Spring Security, BCrypt); every query is scoped to the signed-in user
- Account lockout after 5 wrong passwords in a row (15 minutes, stored in the database)
- Activity log of sign-ins, failed sign-ins, lockouts, and every change, with before/after values

**Everyday polish**
- Amounts shown as ฿1,234.50; currency set in `application.properties`
- Works on phones (rows turn into cards) and follows the system dark mode
- Persistent data in a local file database (survives restarts)

## Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 17 | LTS, light, widely supported |
| Framework | Spring Boot 3.5 (Web, Thymeleaf, Data JPA, Validation, Security) | Covers MVC, DI, beans, and auth |
| Database | H2 in file mode | No server to run, tiny footprint |
| Charts | Chart.js 4 from cdnjs, pinned with an SRI hash | No server memory, no build step |
| Build | Maven (wrapper included) | No global install needed |
| Tests | JUnit 5, MockMvc, DataJpaTest, Spring Security Test | 119 tests |
| CI | GitHub Actions | Runs `./mvnw test` on every push |

Deliberately left out to keep memory low: Actuator, Docker, a front-end framework, and any CSV, chart, or AI library on the server. The only dependencies beyond the Spring Boot starters are H2 and `spring-security-test`.

## Project Structure

```
expense-tracker/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/                  Maven wrapper (.mvn/jvm.config caps Maven at 512 MB)
├── .github/workflows/ci.yml               runs ./mvnw test
├── README.md
├── CLAUDE.md
└── src/
    ├── main/
    │   ├── java/com/example/expenses/
    │   │   ├── ExpenseTrackerApplication.java      (@EnableScheduling)
    │   │   ├── config/
    │   │   │   ├── DemoDataSeeder.java             sample data for the "demo" profile
    │   │   │   └── WebConfig.java
    │   │   ├── model/
    │   │   │   ├── Expense.java, Budget.java, RecurringExpense.java
    │   │   │   ├── User.java, AuditEntry.java
    │   │   │   ├── Category.java, AuditAction.java  (enums)
    │   │   │   └── EnumNameConverter.java (+ CategoryConverter, AuditActionConverter)
    │   │   ├── repository/
    │   │   │   ├── ExpenseRepository.java          grouped JPQL for totals
    │   │   │   ├── ExpenseSpecifications.java      optional filters, always scoped to the owner
    │   │   │   └── BudgetRepository, RecurringExpenseRepository, UserRepository, AuditEntryRepository
    │   │   ├── security/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── AppUserDetails.java, AppUserDetailsService.java
    │   │   │   └── LoginAttemptListener.java, LoginAttemptService.java   (lockout)
    │   │   ├── service/
    │   │   │   ├── ExpenseService, BudgetService, SummaryService, UserService, AuditService
    │   │   │   ├── RecurringExpenseService.java, RecurringExpenseScheduler.java
    │   │   │   ├── CsvExportService.java, CsvImportService.java, Csv.java
    │   │   │   ├── CategorySuggester.java, QuickEntryParser.java
    │   │   │   └── ExpenseNotFoundException, RecurringExpenseNotFoundException, UsernameTakenException
    │   │   ├── web/
    │   │   │   ├── ExpenseController, SummaryController, RecurringController, ImportController
    │   │   │   ├── AuthController, ActivityController
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── MoneyFormatter.java ("@money" in templates), CurrentUserInterceptor.java
    │   │   └── dto/
    │   │       ├── ExpenseForm, BudgetForm, RecurringForm, RegistrationForm
    │   │       └── ExpenseFilter, CategoryTotal, CategorySummary, MonthlySummary, DailyTotal, DailySpending
    │   └── resources/
    │       ├── application.properties, application-demo.properties, messages.properties
    │       ├── static/css/style.css
    │       ├── static/js/app.js, static/js/summary-chart.js
    │       └── templates/
    │           ├── layout.html, error.html, summary.html, recurring.html, activity.html
    │           ├── auth/login.html, auth/register.html
    │           └── expenses/list.html, form.html, import.html, not-found.html
    └── test/
        ├── java/com/example/expenses/   (14 test classes, see Testing)
        └── resources/config/            in-memory database for tests
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

**Budget**: `owner`, `category` (unique per user), `monthlyLimit` (BigDecimal)

**RecurringExpense**: `owner`, `title`, `amount`, `category`, `note`, `dayOfMonth`, `nextDueDate`, `active`

**User**: `username` (unique, case-insensitive), `passwordHash` (BCrypt), `failedAttempts`, `lockedUntil`, `createdAt`

**AuditEntry**: `userId`, `action`, `details`, `createdAt` (append-only)

Money is always `BigDecimal`, never `double`. Enums are stored as plain `varchar` through converters, so adding a value later needs no schema change.

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
| POST | `/expenses/{id}/delete` | Delete |
| GET | `/expenses/export?month=2026-10` | Download CSV (also takes `category` and `q`) |
| GET, POST | `/expenses/import` | Import form, upload CSV |
| GET | `/expenses/suggest-category?title=grab+ride` | JSON category guess (used while typing) |
| GET | `/summary?month=2026-10` | Monthly totals, chart, and budget status |
| POST | `/budgets`, `/budgets/{category}/delete` | Set or remove a budget |
| GET, POST | `/recurring` | List and add recurring expenses |
| POST | `/recurring/{id}/pause`, `/resume`, `/delete` | Manage a recurring expense |
| GET | `/activity` | Your activity log |
| GET, POST | `/login`, `/register` | Sign in, create an account |
| POST | `/logout` | Sign out |

## Run It

Requirements: JDK 17+ (Maven is not needed, the wrapper is included). The commands work as written in PowerShell, Git Bash, macOS and Linux (in the old cmd.exe, type `mvnw` instead of `./mvnw`).

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080 and create an account. Data is stored in `./data/expenses.mv.db`.

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

Add `--spring.profiles.active=demo` to the `java` command for the sample data.

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
| `SummaryServiceTest` | Summary math: no expenses, exactly at the limit, over the limit, sorting, bar widths, daily totals |
| `SummaryControllerTest` | Summary page, over-budget highlight, chart data and table view, budget form |
| `SecurityIntegrationTest` | User A cannot list, open, edit, delete or export user B's expenses; CSRF; login; lockout; registration; audit log; import; recurring |
| `RecurringExpenseServiceTest` | Due dates, day 31 in short months, catch-up, running twice, pause/resume, per-user isolation |
| `CsvExportServiceTest`, `CsvImportServiceTest` | Quoting, CSV injection, parsing, row errors, export then import round trip |
| `QuickEntryParserTest`, `CategorySuggesterTest` | "lunch 85 baht yesterday", dates, weekdays, Thai words, keyword rules |
| `UserLockoutTest`, `MoneyFormatterTest`, `DemoDataSeederTest`, `ExpenseTrackerApplicationTests` | Lockout math, ฿ formatting, demo seed, context starts |

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
| **Uploads** | CSV import is limited to 1 MB and 2,000 rows, is read as text only, and saves nothing unless every row is valid. |
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

## Roadmap Ideas

- Income tracking and a monthly balance
- Tags, receipt photos (stored on disk), splitting expenses with friends
- Insights: month-over-month comparison, end-of-month projection, top 5 expenses, spending by weekday, anomaly alerts
- Savings goals
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

## License

MIT
