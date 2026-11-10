#!/usr/bin/env bash
# Proves the ADR-002 guardrails fire (ADR-002 "Confirmation"). Works on a throw-away copy of the repository:
# it injects one violation per rule and requires the matching check to FAIL for the right reason.
#   1. an illegal module edge             -> ./gradlew assertModuleGraph
#   2. a forbidden import + bare catch    -> ./gradlew :shared:domain:detekt
#   3. a forbidden import in the domain   -> Konsist ArchitectureRulesTest
# (Konsist also proves each of its rules on fixtures, see ArchitectureRulesSelfTest.)
# Usage: tools/verify-guardrails.sh        Runs in CI (shared.yml) and locally; needs a JDK 17+ on PATH.
set -uo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
rsync -a --exclude .git --exclude build --exclude .gradle --exclude .kotlin --exclude node_modules \
    --exclude 'web/' --exclude 'legacy/' --exclude 'docs/' --exclude 'prototypes/' --exclude 'spikes/' "$repo/" "$work/"
cd "$work"

failed=0

expect_failure() { # description, expected text in output, command...
    local description="$1" expected="$2"
    shift 2
    local output
    if output="$("$@" 2>&1)"; then
        echo "FAIL: $description: the check passed but must fail"
        failed=1
    elif ! grep -qF -- "$expected" <<<"$output"; then
        echo "FAIL: $description: the check failed, but not for the expected reason ('$expected')"
        echo "$output" | tail -n 25
        failed=1
    else
        echo "OK:   $description"
    fi
}

# 1. Illegal module edge: pipeline must not depend on presentation (ADR-002 section 2 table).
sed -i.bak 's#implementation(projects.shared.domain)#implementation(projects.shared.domain)\n        implementation(projects.shared.presentation)#' shared/pipeline/build.gradle.kts
expect_failure "module graph rejects :shared:pipeline -> :shared:presentation" ":shared:pipeline" ./gradlew --console=plain assertModuleGraph
mv shared/pipeline/build.gradle.kts.bak shared/pipeline/build.gradle.kts

# 2 and 3. A domain class that imports Ktor and swallows every exception (detekt and Konsist both read sources).
mkdir -p shared/domain/src/commonMain/kotlin/com/driveperf/domain/violation
cat > shared/domain/src/commonMain/kotlin/com/driveperf/domain/violation/Violation.kt <<'KT'
package com.driveperf.domain.violation

import io.ktor.client.HttpClient

internal fun leaky(client: HttpClient): Int =
    try {
        client.hashCode()
    } catch (e: Exception) {
        0
    }
KT
expect_failure "detekt rejects Ktor in :shared:domain" "ForbiddenImport" ./gradlew --console=plain :shared:domain:detekt
expect_failure "detekt rejects a bare catch of Exception" "TooGenericExceptionCaught" ./gradlew --console=plain :shared:domain:detekt

# Konsist reads source files, so the violation can live outside the compiled source sets (the build stays green).
mkdir -p shared/domain/src/guardrailSandbox/kotlin
mv shared/domain/src/commonMain/kotlin/com/driveperf/domain/violation/Violation.kt shared/domain/src/guardrailSandbox/kotlin/Violation.kt
expect_failure "Konsist rejects Ktor in :shared:domain" "domainStaysPure" ./gradlew --console=plain :shared:testing:jvmTest --tests '*ArchitectureRulesTest*'

exit "$failed"
