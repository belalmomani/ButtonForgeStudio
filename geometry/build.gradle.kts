plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}
