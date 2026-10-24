plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(project(":geometry"))
    testImplementation(project(":document"))
    testImplementation(project(":editor-core"))
    testImplementation(project(":project-storage"))
    testImplementation(testFixtures(project(":document")))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Opt-in timing run: gradle :integration-tests:perfTest. Skipped by the normal test task.
tasks.register<Test>("perfTest") {
    useJUnit()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("bfs.perf", "1")
    filter { includeTestsMatching("*PerfBaselineTest") }
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
