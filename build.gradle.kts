import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.wrapper.Wrapper
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
}

group = "com.megharaj"
version = "1.0.0-SNAPSHOT"
description = "Concurrency-safe order, inventory reservation, and payment simulation service."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.bootJar {
    archiveFileName.set("order-inventory-service.jar")
}

tasks.jar {
    enabled = false
}

tasks.wrapper {
    gradleVersion = "8.14.5"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854"
    networkTimeout.set(10_000)
}
