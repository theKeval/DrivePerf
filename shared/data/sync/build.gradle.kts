plugins {
    id("driveperf.kmp-library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
        implementation(projects.shared.domain)
        implementation(projects.shared.data.local)
        implementation(projects.shared.data.remote)
    }
}
