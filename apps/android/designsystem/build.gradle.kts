plugins {
    id("driveperf.android-library")
    id("driveperf.compose")
}

android {
    // Vector icons live in this module; R classes are non-transitive, so features reach them through DpIcons.
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
