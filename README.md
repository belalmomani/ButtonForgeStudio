# Button Forge Studio

Phase 1, step 1: Gradle skeleton + `:geometry` module (pure JVM).

## First-time setup
The Gradle wrapper is not included (it could not be generated offline). Once, on a machine with Gradle installed:

    gradle wrapper --gradle-version 8.10.2

then commit `gradlew`, `gradlew.bat` and `gradle/wrapper/`. Update the unverified versions in `gradle/libs.versions.toml` first.

## Verify
    ./gradlew :geometry:test

## Phase 1, step 2
Adds `:document` (layers, shapes with separate geometry/appearance, button states, export settings, id counters).
Verify: `gradle :geometry:test :document:test --continue`

## Phase 1, step 3
Adds `:project-storage` (.bfs ZIP archive, DTO schema v1, migrations, atomic save, corrupt/future-version handling).
Verify: `gradle :geometry:test :document:test :project-storage:test --continue`
Not included yet: the schema snapshot test (needs one green run to generate the golden schema file).
