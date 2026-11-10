// Convention for the Android app module (apps/android/app).
plugins {
    id("com.android.application")
    id("driveperf.detekt")
    id("driveperf.compose")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}
