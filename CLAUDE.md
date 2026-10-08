# Expense Tracker

## Project
- Expense Tracker: Java 17, Spring Boot 3.x (pinned to 3.5.x in `pom.xml`), Maven wrapper, Thymeleaf, Spring Data JPA, Spring Security, H2 file database.
- Base package: `com.example.expenses`

## Rules
- The machine has limited RAM and disk. Do not add heavy dependencies, Docker, or Actuator. Ask before adding any new dependency.
- Use `BigDecimal` for money.
- Use constructor injection.
- Keep layers separate: controller -> service -> repository.
- After every change, make sure the app compiles and tests pass.
- Tell the user before changing an existing table in a way that needs a data migration (e.g. a NOT NULL column or a backfill). Prefer nullable columns whose NULL keeps the old meaning.

## Commands
- Run: `./mvnw spring-boot:run` (then open http://localhost:8080)
- Run with sample data: `./mvnw spring-boot:run -Pdemo` (sign in as demo / demo1234; uses `./data/demo`). Don't suggest `-Dspring-boot.run.profiles=...` to the user: PowerShell splits it at the dot.
- Run with the H2 console: `./mvnw spring-boot:run -Pdev` (console at /h2-console after signing in; JDBC URL jdbc:h2:file:./data/expenses, user sa, empty password)
- Test: `./mvnw test` (every test runs with the `test` profile: in-memory H2, see src/test/resources)

## Notes
- start.spring.io now only generates Spring Boot 4.x projects. Stay on 3.5.x unless asked to upgrade.
- Expenses are soft-deleted (`deleted_at`). Every expense list, total or lookup must leave the trash out: use `ExpenseSpecifications.matching`, `... and e.deletedAt is null` in JPQL, or the `...DeletedAtIsNull` finders. Only Trash and Backup see trashed rows.
- Every service method takes the signed-in user's id and every query is scoped to it. Keep it that way for new features, and add a case to `SecurityIntegrationTest`.
- Profiles: default (./data/expenses), `dev` (+ H2 console), `demo` (sample data in ./data/demo), `test` (in-memory, set in src/test/resources/config/application.properties). A test that activates another profile must list `test` last, e.g. `@ActiveProfiles({"demo", "test"})`, so the in-memory database wins.
- No secrets in config files: the database password comes from `DB_PASSWORD` (empty by default).
- Enums are stored with `EnumNameConverter` subclasses, not `@Enumerated`: Hibernate adds a CHECK constraint for `@Enumerated` that `ddl-auto=update` never updates.
- Lazy initialization is on: `@Scheduled` beans need `@Lazy(false)`.
- `th:data-*` attributes run in Thymeleaf's restricted mode (no `@bean` references); resolve values with `th:with` first.
- `.properties` files are read as ISO-8859-1: write non-ASCII as `\uXXXX` escapes.
- `.mvn/jvm.config` caps the Maven JVM heap to keep builds light.
