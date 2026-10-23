# Button Forge Studio

Phase 1, step 1: Gradle skeleton + `:geometry` module (pure JVM).

## First-time setup
The Gradle wrapper is not included (it could not be generated offline). Once, on a machine with Gradle installed:

    gradle wrapper --gradle-version 8.10.2

then commit `gradlew`, `gradlew.bat` and `gradle/wrapper/`. Update the unverified versions in `gradle/libs.versions.toml` first.

## Verify
    ./gradlew :geometry:test
