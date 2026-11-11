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

// Drive fixtures (pipeline-and-scoring.md §8.2) live in shared/testing/fixtures. The JVM test compares them with the
// generator; `-PregenerateFixtures=true` rewrites them and their goldens instead.
tasks.named<Test>("jvmTest") {
    val fixtures = rootProject.layout.projectDirectory.dir("shared/testing/fixtures/drives")
    inputs.dir(fixtures).withPropertyName("driveFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("driveperf.fixtures", fixtures.asFile.absolutePath)
    systemProperty("driveperf.regenerateFixtures", providers.gradleProperty("regenerateFixtures").getOrElse("false"))
}
