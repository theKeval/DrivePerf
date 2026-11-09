plugins {
    id("driveperf.kmp-library")
}

// Composition root: the only shared module that may see every other shared module (ADR-002 section 2).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
        implementation(projects.shared.domain)
        implementation(projects.shared.pipeline)
        implementation(projects.shared.presentation)
        implementation(projects.shared.apiContract)
        implementation(projects.shared.data.local)
        implementation(projects.shared.data.remote)
        implementation(projects.shared.data.sync)
    }
}
