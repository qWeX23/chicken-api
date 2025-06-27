# Repository Guidelines

This project is a Kotlin Spring Boot application that exposes an API about chickens and their breeds. Data is fetched from Google Sheets and presented via REST controllers. Hypermedia links are added to responses using **Spring HATEOAS**, allowing clients to navigate the API via the `_links` section of each resource.

When contributing changes:

- **Run `./gradlew spotlessApply`** before committing to automatically format Kotlin and Gradle files.
- **Run `./gradlew test --no-daemon`** to ensure all tests pass.

Follow these steps for every change to keep the codebase consistent and working.
