# Expense Tracker

## Project
- Expense Tracker: Java 17, Spring Boot 3.x (pinned to 3.5.x in `pom.xml`), Maven wrapper, Thymeleaf, Spring Data JPA, H2 file database.
- Base package: `com.example.expenses`

## Rules
- The machine has limited RAM and disk. Do not add heavy dependencies, Docker, or Actuator. Ask before adding any new dependency.
- Use `BigDecimal` for money.
- Use constructor injection.
- Keep layers separate: controller -> service -> repository.
- After every change, make sure the app compiles and tests pass.

## Commands
- Run: `./mvnw spring-boot:run` (then open http://localhost:8080)
- Test: `./mvnw test`

## Notes
- start.spring.io now only generates Spring Boot 4.x projects. Stay on 3.5.x unless asked to upgrade.
- Tests use an in-memory H2 database (`src/test/resources/config/application.properties`), so they never touch `./data`.
- `.mvn/jvm.config` caps the Maven JVM heap to keep builds light.
