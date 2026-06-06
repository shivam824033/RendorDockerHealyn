plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.healyn"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.20.6"
// Override the Spring Boot BOM's managed Testcontainers version (1.19.8). That
// version ships docker-java 3.3.6 (Engine API 1.43), which Docker Engine 28+
// rejects after the minimum API version was raised to 1.44. 1.20.6 carries a
// docker-java that negotiates a supported API version.
extra["testcontainers.version"] = "1.20.6"
extra["springdocVersion"] = "2.6.0"
extra["nimbusJoseVersion"] = "9.41.2"

dependencies {
    // Web + validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Security + JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("com.nimbusds:nimbus-jose-jwt:${property("nimbusJoseVersion")}")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    // Object storage (S3-compatible: MinIO local, AWS S3 / R2 in prod) — Apache 2.0
    implementation("io.minio:minio:8.5.14")

    // Push notifications (FCM dispatch adapter) — Apache 2.0
    implementation("com.google.firebase:firebase-admin:9.4.3")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    // Testcontainers/docker-java picks an Engine API version that the Docker
    // Desktop for Windows npipe/TCP proxy reports as higher than the daemon
    // actually serves, so the daemon rejects /info with HTTP 400 and discovery
    // fails. Pin a broadly-supported version on Windows only; Linux CI keeps
    // auto-negotiation. An exported DOCKER_API_VERSION still wins.
    val pinnedApiVersion = System.getenv("DOCKER_API_VERSION")
        ?: "1.44".takeIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }
    pinnedApiVersion?.let {
        environment("DOCKER_API_VERSION", it)
        systemProperty("api.version", it)
    }
}

springBoot {
    mainClass.set("com.healyn.HealynApplication")
}

// Load repo-root `.env` into `bootRun` so local dev picks up HEALYN_* without
// the developer having to export them. Production runs supply env vars through
// the deployment platform, so this only affects the dev task.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFile = rootProject.file("../.env")
    if (envFile.exists()) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach {
                val idx = it.indexOf('=')
                val key = it.substring(0, idx).trim()
                val value = it.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                environment(key, value)
            }
    }
}
