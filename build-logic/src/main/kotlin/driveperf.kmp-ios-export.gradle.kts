// Convention for `:shared:ios-export` only: iOS targets, no Android or JVM, plus SKIE (ADR-002 section 8).
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("driveperf.detekt")
    id("co.touchlab.skie")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
    iosArm64()
    iosSimulatorArm64()
}
