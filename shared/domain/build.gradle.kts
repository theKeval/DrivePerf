plugins {
    id("driveperf.kmp-library")
    id("driveperf.kover")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.shared.foundation)
    }
}

// PRD 7.2: at least 90 % line coverage on domain and pipeline. :shared:pipeline joins this gate in DRIVE-62,
// when it has code (an empty module has nothing to measure).
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
