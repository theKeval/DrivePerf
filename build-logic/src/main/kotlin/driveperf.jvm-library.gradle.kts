// Convention for the Ktor server module (backend/api).
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("driveperf.detekt")
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions { allWarningsAsErrors.set(true) }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
