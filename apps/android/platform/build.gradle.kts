plugins {
    id("driveperf.android-library")
}

// Sensors, foreground service, WorkManager and Firebase adapters implementing shared ports (ADR-002).
// Content arrives with DRIVE-72.
dependencies {
    implementation(projects.shared.foundation)
    implementation(projects.shared.domain)
}
