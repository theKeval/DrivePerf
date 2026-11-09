package com.driveperf.testing.architecture

import kotlin.test.Test
import kotlin.test.assertTrue

/** The real repository obeys ADR-002 section 2. */
class ArchitectureRulesTest {
    private val files = ArchitectureRules.repositoryFiles()

    @Test
    fun scansTheRepository() {
        assertTrue(files.any { it.path.contains("/shared/domain/") }, "no :shared:domain sources found; wrong scan root?")
    }

    @Test
    fun noRuleIsViolated() {
        val violations = ArchitectureRules.all(files).filterValues { it.isNotEmpty() }
        assertTrue(violations.isEmpty(), violations.entries.joinToString("\n") { (rule, v) -> "$rule:\n  " + v.joinToString("\n  ") })
    }
}
