rootProject.name = "ButtonForgeStudio"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Phase 1, steps 1-6. designsystem is not created: it would hold nothing but a theme wrapper (theme lives in :app).
include(
    ":geometry", ":document", ":project-storage", ":editor-core",
    ":renderer", ":editor-ui", ":geometry-android", ":app", ":integration-tests",
)
