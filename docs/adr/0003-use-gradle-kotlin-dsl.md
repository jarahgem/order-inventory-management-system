# ADR 0003: Use a single-module Gradle Kotlin DSL build

- Status: Accepted
- Date: 2026-08-26

## Context

The assessment is a focused Java service. Its build must remain easy to understand, run,
and review while supporting tests, executable packaging, code coverage, containers, and CI.
The application is already organized as a package-by-feature modular monolith.

The build alternatives considered were:

1. Gradle Groovy DSL or Gradle Kotlin DSL.
2. One Gradle project or a multi-project build.
3. Gradle's native BOM support or the Spring dependency-management plugin.
4. Gradle 8.x or Gradle 9.x while retaining Spring Boot 3.5.16.

## Decision

Use Gradle 8.14.5 with Kotlin DSL in one project.

- Kotlin DSL provides statically typed build configuration and strong IDE assistance. The
  build is small enough that Groovy DSL's terseness does not outweigh those reviewability
  benefits.
- Keep one Gradle project. Package boundaries already express the domain modules, and a
  multi-project build would add publication, dependency, test-fixture, and configuration
  overhead without an independent deployment or ownership need.
- Import the Spring Boot BOM with Gradle's native `platform` support. No managed dependency
  property overrides are required, so an additional dependency-management plugin would add
  little value.
- Retain Spring Boot 3.5.16 and use the latest Gradle 8.14 patch release. Moving to Gradle 9
  would require a separate Spring Boot major-version decision and is outside this build-tool
  migration.
- Pin both the Gradle distribution and Wrapper JAR with SHA-256 checksums. Because this
  generated submission environment cannot retrieve binary artifacts, the repository keeps
  the Wrapper bootstrap in auditable shell/PowerShell source. On first execution it downloads
  the official Wrapper JAR, verifies its published checksum, and then delegates to the normal
  Gradle Wrapper runtime.

## Consequences

### Positive

- Reviewers get one concise, type-safe build file.
- Java 21, test execution, JaCoCo reporting, Spring Boot packaging, and the Wrapper version are
  declared in one place.
- The project remains simple to import into IntelliJ IDEA or Eclipse as a Gradle project.
- Package modules can later become Gradle subprojects if independent ownership, release
  cadence, or deployment boundaries justify the change.

### Trade-offs

- Kotlin DSL can be slightly more verbose than Groovy DSL and incurs Kotlin script compilation
  during initial configuration.
- A single project cannot enforce package-module dependencies at the build graph level.
  Architecture tests or eventual subproject extraction are options if the codebase grows.
- The first Wrapper invocation requires access to GitHub and Gradle's distribution service;
  later invocations use the local Gradle user-home cache.
