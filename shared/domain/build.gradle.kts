plugins {
    id("driveperf.kmp-library")
    id("driveperf.kover")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
        // Repository and recorder contracts expose Flow and StateFlow, so callers need the coroutines types too.
        api(libs.kotlinx.coroutines.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
}

// PRD 7.2: at least 90 % line coverage on domain and pipeline.
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
