plugins {
    id("driveperf.android-application")
}

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

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    debugImplementation(libs.compose.ui.tooling)
}
