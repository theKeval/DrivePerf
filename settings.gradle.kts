// DrivePerf root build (ADR-002): one Gradle build for the KMP core, the Android app and the Ktor server.
// The iOS app is an Xcode project that calls into this build (see apps/ios/README.md); web/ and
// backend/firebase/ are npm projects and are not part of this build; legacy/android-prototype/ and
// spikes/ have their own, separate builds.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "driveperf"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Kotlin Multiplatform core
include(
    ":shared:foundation",
    ":shared:domain",
    ":shared:pipeline",
    ":shared:presentation",
    ":shared:api-contract",
    ":shared:data:local",
    ":shared:data:remote",
    ":shared:data:sync",
    ":shared:di",
    ":shared:ios-export",
    ":shared:testing",
)

// Android app: one feature module per PRD epic (ADR-002 section 1)
include(
    ":apps:android:app",
    ":apps:android:designsystem",
    ":apps:android:platform",
)
listOf(
    "capture", "drives", "replay", "routes", "insights", "garage",
    "account", "privacy", "paywall", "settings", "onboarding",
).forEach { include(":apps:android:feature-$it") }

// Server
include(":backend:api")
