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
            // Release frameworks keep light debug info, so the app's dSYM (uploaded to Sentry by the release lane) can
            // symbolicate Kotlin frames in crash reports (ADR-007, DRIVE-196).
            freeCompilerArgs += "-Xadd-light-debug=enable"
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

    // Sentry Kotlin Multiplatform, for its Kotlin/Native unhandled-exception hook only (KotlinCrashHook). It calls into
    // Sentry Cocoa, which the Xcode project links at the exact version this SDK was built against (libs.versions.toml).
    sourceSets.iosMain.dependencies {
        implementation(libs.sentry.kmp)
    }
}

// SQLDelight's native driver calls the system SQLite. A static framework cannot carry linker flags, so declare the
// library in the framework's module map: Swift and Clang autolinking then add `-lsqlite3` to the app when it imports
// DrivePerfShared, and the Xcode project needs no extra setting. Runs after SKIE has written the module map.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    doLast {
        val moduleMap = outputFile.get().resolve("Modules/module.modulemap")
        if (moduleMap.isFile) {
            val text = moduleMap.readText()
            if (!text.contains("link \"sqlite3\"")) {
                moduleMap.writeText(text.replaceFirst("    use Foundation\n", "    use Foundation\n    link \"sqlite3\"\n"))
            }
        }
    }
}
