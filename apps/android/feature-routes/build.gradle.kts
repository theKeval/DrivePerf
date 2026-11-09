plugins {
    id("driveperf.android-library")
}

// One module per PRD epic (ADR-002 section 1). Features see presentation, domain, foundation and
// designsystem only; the shared data layer, platform adapters and Firebase are reachable through the app.
dependencies {
    implementation(projects.shared.foundation)
    implementation(projects.shared.domain)
    implementation(projects.shared.presentation)
    implementation(projects.apps.android.designsystem)
}
