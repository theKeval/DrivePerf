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
