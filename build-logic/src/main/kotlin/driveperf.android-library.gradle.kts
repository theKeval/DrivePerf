// Convention for the Android feature/designsystem/platform libraries (ADR-002 section 1).
plugins {
    id("com.android.library")
    id("driveperf.detekt")
}

android {
    namespace = "com.driveperf." + project.path.removePrefix(":apps:android:").replace('-', '.')
    compileSdk = 37 // libraries need the newest APIs to compile; targetSdk stays 36 (ADR-001)
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}
