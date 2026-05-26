plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "co.qwex"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val koogVersion = "1.0.0"
val koogBetaVersion = "1.0.0-beta"
val opentelemetryVersion = "1.61.0"
val ktorVersion = "3.3.3"
val okioVersion = "3.17.0"
val serializationVersion = "1.10.0"
val coroutinesVersion = "1.10.2"

configurations.all {
    resolutionStrategy.eachDependency {
        when {
            requested.group == "io.opentelemetry" -> useVersion(opentelemetryVersion)
            requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization") ->
                useVersion(serializationVersion)
            requested.group == "com.squareup.okio" && requested.name.startsWith("okio") -> useVersion(okioVersion)
        }
    }
}

dependencies {
    implementation(platform("io.opentelemetry:opentelemetry-bom:$opentelemetryVersion"))
    implementation("ai.koog:koog-agents:$koogVersion")
    implementation("ai.koog:agents-features-opentelemetry:$koogVersion")
    implementation("ai.koog:http-client-ktor:$koogVersion")
    implementation("ai.koog:prompt-executor-llms-all:$koogBetaVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.opentelemetry:opentelemetry-exporter-sender-okhttp")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.google.api-client:google-api-client:2.4.1")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20230815-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.http-client:google-http-client-jackson2:1.43.3")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.01")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.giffing.bucket4j.spring.boot.starter:bucket4j-spring-boot-starter:0.13.0")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    implementation("com.github.ben-manes.caffeine:jcache:3.2.2")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    environment("GOOGLE_APPLICATION_CREDENTIALS", "/Users/benjaminchurchill/Github/chicken-api/chicken-api-460112-52ea6363b3cd.json")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("build/**")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}
