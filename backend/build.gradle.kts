plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.freefair.lombok") version "8.11"
}

group = "org.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.20.4"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Миграции БД
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Внешние API и Excel
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Jackson и Logback приходят транзитивно из spring-boot-starter-web

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

/**
 * Классы `*IT` поднимают Testcontainers и требуют запущенного Docker, поэтому в `test` их нет:
 * иначе выключенный Docker роняет сборку при полностью исправном коде. `check` их тоже не тянет —
 * интеграционные тесты запускаются осознанно, когда инфраструктура поднята.
 */
tasks.test {
    exclude("**/*IT.class")
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Интеграционные тесты на Testcontainers — нужен запущенный Docker."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*IT.class")
    shouldRunAfter(tasks.test)
}
