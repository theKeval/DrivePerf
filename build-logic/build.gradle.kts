plugins {
    `kotlin-dsl`
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.kotlin.compose.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.skie.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.sqldelight.gradle.plugin)
    implementation(libs.wire.gradle.plugin)
}
