plugins {
    id("driveperf.kmp-library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
        implementation(projects.shared.domain)
    }
}
