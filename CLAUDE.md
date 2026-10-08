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

## Commands
- Run: `./mvnw spring-boot:run` (then open http://localhost:8080)
- Run with sample data: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` (sign in as demo / demo1234; uses `./data/demo`)
- Test: `./mvnw test`

## Notes
- start.spring.io now only generates Spring Boot 4.x projects. Stay on 3.5.x unless asked to upgrade.
- Every service method takes the signed-in user's id and every query is scoped to it. Keep it that way for new features, and add a case to `SecurityIntegrationTest`.
- Tests use in-memory H2 (`src/test/resources/config/`). A new profile with its own datasource needs a matching test override there, because profile files beat `config/application.properties`.
- Enums are stored with `EnumNameConverter` subclasses, not `@Enumerated`: Hibernate adds a CHECK constraint for `@Enumerated` that `ddl-auto=update` never updates.
- Lazy initialization is on: `@Scheduled` beans need `@Lazy(false)`.
- `th:data-*` attributes run in Thymeleaf's restricted mode (no `@bean` references); resolve values with `th:with` first.
- `.properties` files are read as ISO-8859-1: write non-ASCII as `\uXXXX` escapes.
- `.mvn/jvm.config` caps the Maven JVM heap to keep builds light.
