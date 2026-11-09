// detekt with the repo-wide config (config/detekt/detekt.yml). Applied by every Kotlin convention plugin.
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    id("io.gitlab.arturbosch.detekt")
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "src/commonMain/kotlin", "src/commonTest/kotlin",
            "src/androidMain/kotlin", "src/iosMain/kotlin", "src/jvmMain/kotlin", "src/jvmTest/kotlin",
            "src/main/kotlin", "src/test/kotlin",
        ),
    )
}
