# Architecture-rule violation fixtures

These Kotlin files break the ADR-002 import rules **on purpose**. They are test resources: never compiled, never
scanned by the real rules (`ArchitectureRules.repositoryFiles()` skips `/violations/`) and excluded from detekt.
`ArchitectureRulesSelfTest` fails if any rule stops firing on its fixture. The directory layout mirrors the real
repository because rules decide module membership from the path.
