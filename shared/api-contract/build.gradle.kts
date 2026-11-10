plugins {
    id("driveperf.kmp-library")
    id("driveperf.serialization")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.serialization.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.serialization.json)
    }
}
