# Expense Tracker

A lightweight personal expense tracker built with **Java 17 and Spring Boot**. Log what you spend, filter by month and category, and see where your money goes. It runs in roughly 150-250 MB of RAM with no Docker and no database server.

## Features

- Add, edit, and delete expenses (title, amount, category, date, note)
- Filter by month and category, with pagination
- Monthly summary: total spent and per-category totals with simple bars
- Monthly budget per category, with a warning when you go over
- CSV export of the current filter
- Input validation and friendly error messages
- Persistent data in a local file database (survives restarts)

## Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 17 | LTS, light, widely supported |
| Framework | Spring Boot 3.x (Web, Thymeleaf, Data JPA, Validation) | Covers MVC, DI, and beans |
| Database | H2 in file mode | No server to run, tiny footprint |
| Build | Maven (wrapper included) | No global install needed |
| Tests | JUnit 5, MockMvc, DataJpaTest | Shows testing skills |

Deliberately left out to keep memory low: Actuator, Spring Security (planned for phase 5), Docker, heavy front-end frameworks.

## Project Structure

```
expense-tracker/
├── pom.xml
├── README.md
├── CLAUDE.md
└── src/
    ├── main/
    │   ├── java/com/example/expenses/
    │   │   ├── ExpenseTrackerApplication.java
    │   │   ├── model/
    │   │   │   ├── Expense.java
    │   │   │   ├── Category.java            (enum)
    │   │   │   └── Budget.java
    │   │   ├── repository/
    │   │   │   ├── ExpenseRepository.java
    │   │   │   └── BudgetRepository.java
    │   │   ├── service/
    │   │   │   ├── ExpenseService.java
    │   │   │   ├── SummaryService.java
    │   │   │   └── CsvExportService.java
    │   │   ├── web/
    │   │   │   ├── ExpenseController.java
    │   │   │   ├── SummaryController.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   └── dto/
    │   │       ├── ExpenseForm.java
    │   │       └── CategoryTotal.java
    │   └── resources/
    │       ├── application.properties
    │       ├── static/css/style.css
    │       └── templates/
    │           ├── layout.html
    │           ├── expenses/list.html
    │           ├── expenses/form.html
    │           └── summary.html
    └── test/java/com/example/expenses/
        ├── ExpenseRepositoryTest.java
        ├── ExpenseServiceTest.java
        └── ExpenseControllerTest.java
```

## Data Model

**Expense**

| Field | Type | Rules |
|---|---|---|
| id | Long | auto-generated |
| title | String | required, max 100 chars |
| amount | BigDecimal | required, greater than 0, 2 decimal places |
| category | Category (enum) | required: FOOD, TRANSPORT, RENT, STUDY, HEALTH, FUN, OTHER |
| date | LocalDate | required, not in the future |
| note | String | optional, max 255 chars |

**Budget**: `id`, `category` (unique), `monthlyLimit` (BigDecimal)

Use `BigDecimal` for money, never `double`.

## Routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Redirect to `/expenses` |
| GET | `/expenses?month=2026-10&category=FOOD&page=0` | List with filters |
| GET | `/expenses/new` | New expense form |
| POST | `/expenses` | Create |
| GET | `/expenses/{id}/edit` | Edit form |
| POST | `/expenses/{id}` | Update |
| POST | `/expenses/{id}/delete` | Delete |
| GET | `/summary?month=2026-10` | Monthly totals and budget status |
| GET | `/expenses/export?month=2026-10` | Download CSV |

## Run It

Requirements: JDK 17+ (Maven is not needed, the wrapper is included).

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080.

### Low-memory run

```bash
./mvnw clean package -DskipTests
java -Xmx256m -Xms64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar target/expense-tracker-0.0.1-SNAPSHOT.jar
```

- `-Xmx256m` caps the heap
- `-XX:+UseSerialGC` uses the lightest garbage collector
- `-XX:TieredStopAtLevel=1` trades peak speed for lower memory and faster startup

### Key configuration (`application.properties`)

```properties
spring.datasource.url=jdbc:h2:file:./data/expenses;AUTO_SERVER=FALSE
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
spring.thymeleaf.cache=true
server.tomcat.threads.max=20
spring.main.lazy-initialization=true
```

## Testing

```bash
./mvnw test
```

- Repository tests for month and category queries
- Service tests for summary math and budget warnings
- MockMvc tests for validation errors and redirects

## Build Plan

| Phase | Goal | Time |
|---|---|---|
| 1 | Project setup, entity, repository, CRUD pages | 1 day |
| 2 | Validation, error handling, month/category filter, pagination | 1 day |
| 3 | Summary page, budgets, over-budget warnings | 1 day |
| 4 | CSV export, tests, styling | 1 day |
| 5 (optional) | Login with Spring Security, per-user expenses | 2-3 days |

## Roadmap Ideas

- Per-user accounts with Spring Security and BCrypt
- Recurring expenses (rent, subscriptions)
- Chart.js charts loaded from a CDN
- Switch H2 to SQLite or PostgreSQL through config only
- REST API plus OpenAPI docs

## What This Project Demonstrates

- Spring MVC, dependency injection, and bean configuration
- JPA entities, derived and custom queries, pagination
- Bean Validation with server-side error display
- Layered architecture (controller, service, repository)
- Unit and slice testing
- Resource-conscious deployment on small machines

## License

MIT
