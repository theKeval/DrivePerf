plugins {
    id("driveperf.kmp-library")
}

// Test-only module (ADR-002 section 9): fakes for every port, fixtures and TestClock arrive with DRIVE-62.
// Today it hosts the architecture guardrails: Konsist rules that check imports across the whole repository.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
        implementation(projects.shared.domain)
    }
    sourceSets.jvmTest.dependencies {
        implementation(libs.konsist)
    }
}

// The Konsist architecture rules read Kotlin sources from the whole repository at run time. Declare those files as
// task inputs so a change anywhere (for example in :shared:presentation) re-runs the rules instead of reusing a
// cached or up-to-date passing result.
tasks.named<Test>("jvmTest") {
    inputs.files(
        rootProject.fileTree(rootProject.projectDir) {
            include("shared/**/*.kt", "apps/**/*.kt", "backend/api/**/*.kt")
            exclude("**/build/**")
        },
    ).withPropertyName("scannedSources").withPathSensitivity(PathSensitivity.RELATIVE)
}
