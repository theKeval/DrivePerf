plugins {
    id("driveperf.kmp-ios-export")
}

// Umbrella static framework "DrivePerfShared" for the Xcode project (ADR-002 section 8). It exports only
// foundation, domain, presentation and the di facade; data modules stay internal. Xcode builds it through the
// `embedAndSignAppleFrameworkForXcode` Run Script phase (apps/ios/README.md); SKIE adds Swift-friendly
// coroutines, flows and sealed classes.
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "DrivePerfShared"
            isStatic = true
            export(projects.shared.foundation)
            export(projects.shared.domain)
            export(projects.shared.presentation)
            export(projects.shared.di)
        }
    }

    sourceSets.commonMain.dependencies {
        api(projects.shared.foundation)
        api(projects.shared.domain)
        api(projects.shared.presentation)
        api(projects.shared.di)
    }
}
