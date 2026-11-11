plugins {
    id("driveperf.android-library")
    id("driveperf.compose")
}

android {
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// One module per PRD epic (ADR-002 section 1). Features see presentation, domain, foundation and designsystem only;
// the shared data layer, platform adapters and Firebase are reachable through the app (the composition root).
dependencies {
    implementation(projects.shared.foundation)
    implementation(projects.shared.domain)
    implementation(projects.shared.presentation)
    implementation(projects.apps.android.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
