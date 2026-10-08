pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "watchthetime"

// Phone-only for now (watch app deferred by product decision, 2026-10-08).
// Modules are added here as they get a build.gradle.kts.
include(
    ":core-domain",
    ":core-data",
    ":core-feedback",
    ":core-brand",
    ":phone",
    // ":core-sync",     // later, only when the watch app is built
    // ":wear",          // deferred
)
