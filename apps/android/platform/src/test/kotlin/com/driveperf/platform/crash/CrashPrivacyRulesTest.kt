package com.driveperf.platform.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rule that keeps locations out of crash-report keys and logs (DRIVE-196 AC1, ADR-007), over the Kotlin and Swift
 * sources of both apps and the shared code. Production sources only; tests may say "latitude".
 *
 * 1. Only the two crash adapters talk to Sentry's scope (tags, extras, contexts, user, breadcrumbs).
 * 2. A tag key written there must be on the [CrashTag] allow-list.
 * 3. No log or print call mentions a coordinate or a location type.
 */
class CrashPrivacyRulesTest {
    @Test
    fun `production sources follow the crash privacy rules`() {
        val root = generateSequence(File(checkNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "apps").isDirectory }
        val sources = listOf("apps", "shared").flatMap { dir ->
            File(root, dir).walkTopDown()
                .onEnter { it.name !in skippedDirs }
                .filter { it.isFile && (it.extension == "kt" || it.extension == "swift") }
                .toList()
        }
        assertTrue("found no sources under $root", sources.size > 100)
        val violations = sources.flatMap { file -> CrashPrivacyRules.violations(file.relativeTo(root).path, file.readText()) }
        assertEquals("Crash privacy violations:\n" + violations.joinToString("\n"), emptyList<String>(), violations)
    }

    @Test
    fun `the rules fire on the patterns they exist for`() {
        val kotlin = """
            Sentry.setTag("street", name)
            Log.d("Fix", "at ${'$'}{fix.latitude}")
            println(sample.location)
        """.trimIndent()
        val feature = CrashPrivacyRules.violations("apps/android/feature-x/src/main/kotlin/X.kt", kotlin)
        assertEquals(3, feature.size)
        assertTrue(feature.first().endsWith("crash tags or context"))

        val swift = """
            SentrySDK.configureScope { ${'$'}0.setTag(value: "x", key: "route") }
            logger.info("Fix \(location.coordinate.latitude)")
            print(CLLocation(latitude: 1, longitude: 2))
        """.trimIndent()
        assertEquals(3, CrashPrivacyRules.violations("apps/ios/DrivePerf/Thing.swift", swift).size)

        val adapter = """options.setTag(CrashTag.APP_BUILD.key, appBuild)
            scope.setTag("in_trip", value)
            scope.setTag("street", value)"""
        assertEquals(
            listOf("${CrashPrivacyRules.ANDROID_ADAPTER}:3 tag key \"street\" is not allow-listed"),
            CrashPrivacyRules.violations(CrashPrivacyRules.ANDROID_ADAPTER, adapter),
        )
        assertEquals(emptyList<String>(), CrashPrivacyRules.violations("apps/ios/X.swift", "logger.error(\"Could not save: \\(error)\")"))
    }

    private companion object {
        val skippedDirs = setOf("build", ".build", ".gradle", ".kotlin", "DerivedData", "node_modules", "test", "Tests",
            "androidTest", "jvmTest", "commonTest", "iosTest", "androidHostTest", "androidUnitTest", "DrivePerfTests",
            "DrivePerfUITests")
    }
}

/** The checks, on one file's text, so they can be tested on samples. */
internal object CrashPrivacyRules {
    const val ANDROID_ADAPTER = "apps/android/platform/src/main/kotlin/com/driveperf/platform/crash/SentryCrashReporter.kt"
    const val IOS_ADAPTER = "apps/ios/DrivePerf/Composition/AppEnvironment.swift"

    private val scopeCall = Regex(
        """\b(Sentry|SentrySDK)\s*\.\s*(setTag|setExtra|setExtras|setContexts?|setUser|configureScope|addBreadcrumb)\b""" +
            """|\b(scope|options|\$0)\??\.(setTag|setExtra|setExtras|setContexts?|setUser)\b""",
    )
    private val tagKey = Regex("""setTag\(\s*"([^"]+)"|key:\s*"([^"]+)"""")
    private val logCall = Regex("""\bLog\.[vdiwe]\(|\bprintln\(|(?<![\w.])print\(|\bNSLog\(|\bos_log\(|\blogger\.\w+\(""")
    private val locationWord = Regex(
        """latitude|longitude|\blat\b|\blng\b|\blon\b|coordinate|CLLocation\b|LocationSample|LatLng|\.location\b|\bbbox\b""",
        RegexOption.IGNORE_CASE,
    )

    fun violations(path: String, text: String): List<String> {
        val adapter = path.replace('\\', '/').let { it == ANDROID_ADAPTER || it == IOS_ADAPTER }
        return text.lineSequence().flatMapIndexed { index, line ->
            val at = "$path:${index + 1}"
            buildList {
                if (!adapter && scopeCall.containsMatchIn(line)) add("$at only the crash adapters may set crash tags or context")
                if (adapter) tagKey.findAll(line).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                    .filter { it !in CrashTag.allowedKeys }
                    .forEach { add("$at tag key \"$it\" is not allow-listed") }
                if (logCall.containsMatchIn(line) && locationWord.containsMatchIn(line)) add("$at a log line mentions a location")
            }
        }.toList()
    }
}
