package com.driveperf.testing.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * Import rules from ADR-002 section 2 ("Enforcement", item 3). Each rule returns the violations it found, one
 * line per offending import, so the same rule runs against the real tree (must be empty) and against
 * `src/jvmTest/resources/violations/` (must fire; see [ArchitectureRulesSelfTest]).
 *
 * Module membership is decided from the file path (`/shared/domain/`, ...), which is identical in the real tree
 * and in the violations fixtures.
 */
internal object ArchitectureRules {
    private val sdkImports = listOf(
        "io.ktor.", "app.cash.sqldelight.", "com.squareup.wire.", "com.google.firebase.", "com.revenuecat.", "io.sentry.",
    )
    private const val serverPath = "/backend/api/src/main/kotlin/com/driveperf/server"
    private const val serverPackage = "com.driveperf.server"
    private val platformImports = listOf("platform.", "android.", "androidx.")

    /** Domain: no SQLDelight, Ktor, Wire, kotlinx.serialization, Firebase, RevenueCat, `platform.*`, `android.*`. */
    fun domainStaysPure(files: List<KoFileDeclaration>): List<String> =
        forbid(files, "/shared/domain/", sdkImports + "kotlinx.serialization." + platformImports)

    /** Pipeline: same as domain, and no ambient clock (only an injected `Clock`). */
    fun pipelineStaysPure(files: List<KoFileDeclaration>): List<String> =
        forbid(files, "/shared/pipeline/", sdkImports + "kotlinx.serialization." + platformImports) +
            files.filter { it.path.contains("/shared/pipeline/") && ambientClock.containsMatchIn(it.text) }
                .map { "${it.path}: uses an ambient clock; inject Clock instead" }

    /** Presentation: depends on foundation and domain only; never data, api-contract or an SDK. */
    fun presentationAvoidsData(files: List<KoFileDeclaration>): List<String> =
        forbid(files, "/shared/presentation/", sdkImports + platformImports + "com.driveperf.data." + "com.driveperf.api.contract.")

    /** Foundation: stdlib, coroutines and datetime only (platform actuals live in androidMain/iosMain/jvmMain). */
    fun foundationAvoidsSdks(files: List<KoFileDeclaration>): List<String> =
        forbid(files, "/shared/foundation/src/commonMain/", sdkImports + "kotlinx.serialization." + platformImports + "com.driveperf.")

    /** Android feature modules: no data, platform or Firebase imports; they reach the core through presentation. */
    fun androidFeaturesAvoidAdapters(files: List<KoFileDeclaration>): List<String> =
        forbid(files, "/apps/android/feature-", sdkImports + "com.driveperf.data." + "com.driveperf.platform.")

    /** Server: `application` has no Ktor or adapters; `routes` never touch adapters directly (ADR-002 section 6). */
    fun serverLayersPointInward(files: List<KoFileDeclaration>): List<String> =
        forbid(files, "$serverPath/application/", listOf("io.ktor.", "$serverPackage.adapters.", "$serverPackage.routes.")) +
            forbid(files, "$serverPath/ports/", listOf("io.ktor.", "$serverPackage.adapters.", "$serverPackage.routes.")) +
            forbid(files, "$serverPath/routes/", listOf("$serverPackage.adapters."))

    fun all(files: List<KoFileDeclaration>): Map<String, List<String>> = mapOf(
        "domainStaysPure" to domainStaysPure(files),
        "pipelineStaysPure" to pipelineStaysPure(files),
        "presentationAvoidsData" to presentationAvoidsData(files),
        "foundationAvoidsSdks" to foundationAvoidsSdks(files),
        "androidFeaturesAvoidAdapters" to androidFeaturesAvoidAdapters(files),
        "serverLayersPointInward" to serverLayersPointInward(files),
    )

    /** Kotlin sources of the real repository (never the violations fixtures, never build output). */
    fun repositoryFiles(): List<KoFileDeclaration> {
        return listOf("shared", "apps", "backend/api")
            .flatMap { dir -> Konsist.scopeFromDirectory(dir).files }
            .filterNot { it.path.contains("/build/") || it.path.contains("/violations/") }
    }

    /** Fixture sources that break every rule on purpose. */
    fun violationFiles(): List<KoFileDeclaration> =
        Konsist.scopeFromDirectory("shared/testing/src/jvmTest/resources/violations").files

    private val ambientClock = Regex("""Clock\.System|System\.currentTimeMillis|System\.nanoTime|Instant\.now\(""")

    private fun forbid(files: List<KoFileDeclaration>, pathPart: String, forbidden: List<String>): List<String> =
        files.filter { it.path.contains(pathPart) }.flatMap { file ->
            file.imports.filter { import -> forbidden.any { import.name.startsWith(it) } }
                .map { "${file.path}: forbidden import ${it.name}" }
        }
}
