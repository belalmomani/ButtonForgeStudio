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

// Phase 1, steps 1-3: only modules that exist are included.
include(":geometry", ":document", ":project-storage")
