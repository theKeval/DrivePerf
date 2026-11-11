plugins {
    id("driveperf.android-application")
}

// Crash reporting DSN (ADR-007): injected at build time, never committed. Pass `-Pdriveperf.sentryDsn=...` or set
// DRIVEPERF_SENTRY_DSN. Blank means the Sentry account is not set up and crash reporting is a no-op stub.
val sentryDsn: Provider<String> = providers.gradleProperty("driveperf.sentryDsn")
    .orElse(providers.environmentVariable("DRIVEPERF_SENTRY_DSN"))
    .orElse("")

android {
    namespace = "com.driveperf.app"
    compileSdk = 37 // libraries need the newest APIs to compile; targetSdk stays 36 (ADR-001)

    defaultConfig {
        applicationId = "com.driveperf.app"
        minSdk = 29
        targetSdk = 36
        // Build number = CI run number (ADR-008); the marketing version is set at release time.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"
        buildConfigField("String", "SENTRY_DSN", "\"${sentryDsn.get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Environments (ADR-008): `dev` installs next to `prod` with its own application ID.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
        }
        create("prod") {
            dimension = "environment"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric Compose UI tests
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // ADR-001: targetSdk 36 on purpose; it moves to 37 when Play requires it, after a compatibility pass.
        disable += "OldTargetApi"
    }
}

dependencies {
    // The app is the composition root: it may depend on everything (ADR-002 section 2).
    implementation(projects.shared.foundation)
    implementation(projects.shared.domain)
    implementation(projects.shared.presentation)
    implementation(projects.shared.di)
    implementation(projects.apps.android.designsystem)
    implementation(projects.apps.android.platform)
    implementation(projects.apps.android.featureCapture)
    implementation(projects.apps.android.featureDrives)
    implementation(projects.apps.android.featureReplay)
    implementation(projects.apps.android.featureRoutes)
    implementation(projects.apps.android.featureInsights)
    implementation(projects.apps.android.featureGarage)
    implementation(projects.apps.android.featureAccount)
    implementation(projects.apps.android.featurePrivacy)
    implementation(projects.apps.android.featurePaywall)
    implementation(projects.apps.android.featureSettings)
    implementation(projects.apps.android.featureOnboarding)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// android.yml runs `:apps:android:app:detekt` and `:apps:android:app:testDevDebugUnitTest` only. Hooking the library
// modules onto those two tasks keeps their checks and unit tests in CI without a workflow change.
val libraryModules = listOf("designsystem", "platform") + listOf(
    "capture", "drives", "routes", "insights", "garage", "account", "onboarding",
).map { "feature-$it" }
tasks.named("detekt") {
    dependsOn(libraryModules.map { ":apps:android:$it:detekt" })
}
tasks.matching { it.name == "testDevDebugUnitTest" }.configureEach {
    dependsOn(libraryModules.filter { it != "designsystem" }.map { ":apps:android:$it:testDebugUnitTest" })
}
