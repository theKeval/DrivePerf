plugins {
    id("driveperf.kmp-library")
    id("driveperf.kover")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
        implementation(projects.shared.domain)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.shared.testing)
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
