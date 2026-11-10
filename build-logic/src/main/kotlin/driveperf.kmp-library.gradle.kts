// Convention for every `:shared:*` Kotlin Multiplatform module (ADR-002): Android + iOS + JVM targets,
// the repo's compiler settings and detekt. Module boundaries are enforced by assertModuleGraph,
// Konsist and detekt (see docs/architecture/guardrails.md).
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("driveperf.detekt")
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions { allWarningsAsErrors.set(true) }

    android {
        namespace = "com.driveperf." + project.path.removePrefix(":").replace(':', '.').replace('-', '.')
        compileSdk = 37 // libraries need the newest APIs to compile; targetSdk stays 36 (ADR-001)
        minSdk = 29
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTestBuilder {}
    }
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

// Show assertion messages in the console: the architecture rules report the offending imports there.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
