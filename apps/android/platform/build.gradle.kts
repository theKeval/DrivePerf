plugins {
    id("driveperf.android-library")
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    lint {
        // Lint treats warnings as errors in this repo (see the app module); the shared library convention does not set it.
        warningsAsErrors = true
        abortOnError = true
    }
}

// Sensors, foreground service, activity recognition and crash reporting, implementing the shared ports (ADR-002).
// Platform code delivers samples and runs the OS machinery; it never decides whether a trip exists, how a sample
// is filtered for scoring or what a score is (pipeline-and-scoring.md section 0.1).
dependencies {
    implementation(projects.shared.foundation)
    implementation(projects.shared.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    implementation(libs.koin.android)
    implementation(libs.sentry.android)

    testImplementation(projects.shared.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
}
