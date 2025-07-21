# API Rate Limiting with Bucket4j Spring Boot Starter

This document explains the implementation of API rate limiting in this Spring Boot Kotlin application using `bucket4j-spring-boot-starter` and Caffeine as the caching provider.

## 1. Introduction

Rate limiting is a crucial mechanism to control the rate at which clients can access your API. It helps prevent abuse, protect against denial-of-service (DoS) attacks, and ensure fair usage of resources.

## 2. Changes Made

The following changes were implemented to introduce rate limiting:

### 2.1. `build.gradle.kts`

The following dependencies were added to enable `bucket4j` and Caffeine caching:

```gradle
    implementation("com.giffing.bucket4j.spring.boot.starter:bucket4j-spring-boot-starter:0.13.0")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    implementation("com.github.ben-manes.caffeine:jcache:3.2.2")
```

### 2.2. `src/main/resources/application.properties`

The `application.properties` file was updated to configure Spring's caching and define a global rate limit:

```properties
spring.cache.type=jcache
spring.cache.cache-names=buckets
spring.cache.caffeine.spec=maximumSize=1000000,expireAfterAccess=3600s

bucket4j.enabled=true
bucket4j.cache-to-use=jcache
bucket4j.filters[0].cache-name=buckets
bucket4j.filters[0].url=.*
bucket4j.filters[0].rate-limits[0].bandwidths[0].capacity=10
bucket4j.filters[0].rate-limits[0].bandwidths[0].time=1
bucket4j.filters[0].rate-limits[0].bandwidths[0].unit=minutes
```

-   `spring.cache.type=jcache`: Configures Spring to use JCache as its caching abstraction.
-   `spring.cache.cache-names=buckets`: Defines a cache named "buckets" that `bucket4j` will use.
-   `spring.cache.caffeine.spec`: Configures Caffeine-specific properties for the cache.
-   `bucket4j.enabled=true`: Enables the `bucket4j-spring-boot-starter`.
-   `bucket4j.cache-to-use=jcache`: Explicitly tells `bucket4j` to use the JCache implementation.
-   `bucket4j.filters[0]`: Defines the first (and currently only) rate limiting filter.
    -   `cache-name=buckets`: Links this filter to the "buckets" cache.
    -   `url=.*`: Applies this filter to all incoming requests.
    -   `rate-limits[0].bandwidths[0].capacity=10`: Sets the capacity of the token bucket to 10 requests.
    -   `rate-limits[0].bandwidths[0].time=1`: Sets the refill time to 1 unit.
    -   `rate-limits[0].bandwidths[0].unit=minutes`: Sets the time unit to minutes, resulting in 10 requests per minute.

### 2.3. `src/main/kotlin/co/qwex/chickenapi/ChickenApiApplication.kt`

The main application class was updated to enable Spring's caching abstraction and remove any conflicting manual `CacheManager` bean definitions:

```kotlin
// ... other imports
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@Import(GoogleSheetsConfig::class) // Assuming GoogleSheetsConfig is still needed
@EnableCaching // Enables Spring's caching abstraction
class ChickenApiApplication

// ... main function
```

### 2.4. `src/main/kotlin/co/qwex/chickenapi/controller/BreedController.kt`

The `getAllBreeds` method in `BreedController.kt` was annotated with `@RateLimit` for per-endpoint rate limiting:

```kotlin
package co.qwex.chickenapi.controller

import com.giffing.bucket4j.spring.boot.starter.config.annotations.RateLimit
import java.util.concurrent.TimeUnit

// ... other imports

@RestController()
@RequestMapping("api/v1/breeds/")
class BreedController(
    // ...
) {

    @RateLimit(capacity = 5, refillInterval = 10, refillTimeUnit = TimeUnit.SECONDS)
    @GetMapping()
    fun getAllBreeds(
        @RequestParam(required = false) name: String?,
    ): List<EntityModel<Breed>> {
        // ...
    }

    // ... other methods
}
```

-   `@RateLimit(capacity = 5, refillInterval = 10, refillTimeUnit = TimeUnit.SECONDS)`: This annotation applies a rate limit of 5 requests every 10 seconds specifically to the `getAllBreeds` endpoint. Note that the most restrictive limit (between global and annotation-based) will apply.

## 3. How it Works

-   **Spring Cache Abstraction:** Spring Boot's `@EnableCaching` annotation activates its caching abstraction.
-   **Caffeine as JCache Provider:** The `spring.cache.type=jcache` and Caffeine dependencies tell Spring to use Caffeine as the underlying JCache provider.
-   **Bucket4j Integration:** `bucket4j-spring-boot-starter` auto-detects the JCache `CacheManager` provided by Spring and uses it to store and manage the rate limiting buckets.
-   **Global Filters:** The configuration in `application.properties` defines global rate limiting rules that apply to all requests matching the `url` pattern.
-   **Annotation-based Rate Limiting:** The `@RateLimit` annotation provides a way to apply more specific rate limits to individual controller methods or classes. If both a global filter and an annotation apply to an endpoint, the most restrictive limit will be enforced.

## 4. Maintenance and Configuration Guidelines

### 4.1. Adjusting Global Rate Limiting

To change the global rate limit, modify the `bucket4j.filters[0].rate-limits[0]` properties in `src/main/resources/application.properties`:

-   `capacity`: The maximum number of requests allowed.
-   `time`: The duration for the refill period.
-   `unit`: The time unit (e.g., `seconds`, `minutes`, `hours`).

### 4.2. Per-Endpoint Rate Limiting

To apply or modify rate limits for specific endpoints, use the `@RateLimit` annotation on your controller methods or classes:

```kotlin
@RateLimit(capacity = 10, refillInterval = 1, refillTimeUnit = TimeUnit.MINUTES)
@GetMapping("/some-endpoint")
fun someEndpoint(): String {
    // ...
}
```

-   `capacity`: The number of tokens (requests) available in the bucket.
-   `refillInterval`: The time interval after which the bucket is refilled.
-   `refillTimeUnit`: The time unit for `refillInterval` (e.g., `TimeUnit.SECONDS`, `TimeUnit.MINUTES`).

### 4.3. Cache Configuration

You can adjust Caffeine cache properties in `src/main/resources/application.properties` under `spring.cache.caffeine.spec`:

-   `maximumSize`: Maximum number of entries in the cache.
-   `expireAfterAccess`: How long an entry should remain in the cache after its last access.

### 4.4. Adding New Filters

You can add more rate limiting filters in `application.properties` for different URL patterns or `cache-key` strategies (e.g., rate limit by IP address, user ID):

```properties
bucket4j.filters[1].cache-name=user-buckets
bucket4j.filters[1].url=/api/v1/users/.*
bucket4j.filters[1].rate-limits[0].bandwidths[0].capacity=50
bucket4j.filters[1].rate-limits[0].bandwidths[0].time=1
bucket4j.filters[1].rate-limits[0].bandwidths[0].unit=hours
bucket4j.filters[1].rate-limits[0].cache-key=@httpServletRequest.remoteAddr
```

### 4.5. Troubleshooting

-   **"No Bucket4j cache configuration found"**: Ensure `spring.cache.type=jcache` is set, `bucket4j.cache-to-use=jcache` is present, and the necessary Caffeine and JCache dependencies are in `build.gradle.kts`. Also, confirm `@EnableCaching` is on your main application class.
-   **Incorrect Rate Limiting**: Verify that the `capacity`, `time`, and `unit` values are correctly set in `application.properties` or the `@RateLimit` annotation. Remember that the most restrictive limit applies.

### 4.6. Testing

To verify the rate limiting:

1.  Build the application: `./gradlew build`
2.  Run the application: `export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/credentials.json" && ./gradlew bootRun`
3.  Make repeated requests to the rate-limited endpoint (e.g., `/api/v1/breeds/`) and observe the HTTP 429 (Too Many Requests) response after exceeding the configured limit.
