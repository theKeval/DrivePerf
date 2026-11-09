package com.driveperf.testing.architecture

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ADR-002 "Confirmation": every rule is proven to fire. The fixtures in `src/jvmTest/resources/violations/`
 * break each rule on purpose; if a rule stops catching its fixture it has silently become a no-op.
 */
class ArchitectureRulesSelfTest {
    private val fixtures = ArchitectureRules.violationFiles()

    @Test
    fun fixturesExist() {
        assertTrue(fixtures.size >= 6, "expected at least one violation fixture per rule, found ${fixtures.size}")
    }

    @Test
    fun everyRuleFiresOnItsFixture() {
        val silent = ArchitectureRules.all(fixtures).filterValues { it.isEmpty() }.keys
        assertTrue(silent.isEmpty(), "rules that did not fire on their fixture: $silent")
    }
}
