plugins {
    id("driveperf.kmp-library")
    id("driveperf.serialization")
    id("driveperf.sqldelight")
    id("driveperf.wire")
}

// Device persistence (ADR-003): SQLDelight for rows, Wire protobuf for trip files, kotlinx.serialization for the
// JSON value-object columns (data-model.md §2.2).
kotlin {
    // Wire's generated decoders use `!!` on values Kotlin 2.4 already knows are non-null. Silence only that
    // diagnostic so warnings stay errors for hand-written code.
    compilerOptions { freeCompilerArgs.add("-Xwarning-level=UNNECESSARY_NOT_NULL_ASSERTION:disabled") }

    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
        implementation(projects.shared.domain)
        implementation(libs.sqldelight.runtime)
        implementation(libs.sqldelight.coroutines)
        implementation(libs.wire.runtime)
        implementation(libs.kotlinx.serialization.json)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.sqldelight.android.driver)
    }
    sourceSets.iosMain.dependencies {
        implementation(libs.sqldelight.native.driver)
    }
    sourceSets.jvmMain.dependencies {
        implementation(libs.sqldelight.sqlite.driver)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.shared.testing)
        implementation(libs.kotlinx.coroutines.test)
    }
}

sqldelight {
    databases {
        create("DrivePerfDatabase") {
            packageName.set("com.driveperf.data.local.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

wire {
    kotlin {}
}
