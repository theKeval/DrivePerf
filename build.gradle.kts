// Root build: the module-graph guardrail (ADR-002 section 2, enforcement 2). The allow-list below mirrors
// the dependency table in the ADR. Any module edge that is not listed fails `./gradlew assertModuleGraph`,
// which runs on every PR. A new edge needs an ADR-002 amendment first.
plugins {
    alias(libs.plugins.module.graph.assertion)
}

moduleGraphAssert {
    configurations = setOf(
        "api", "implementation", "commonMainApi", "commonMainImplementation",
        "androidMainImplementation", "iosMainImplementation", "jvmMainImplementation",
    )
    allowed = arrayOf(
        // :shared (pure layers)
        ":shared:domain -> :shared:foundation",
        ":shared:pipeline -> :shared:(foundation|domain)",
        ":shared:presentation -> :shared:(foundation|domain)",
        // :shared (adapters)
        ":shared:data:local -> :shared:(foundation|domain)",
        ":shared:data:remote -> :shared:(foundation|domain|api-contract)",
        ":shared:data:sync -> :shared:(foundation|domain|data:local|data:remote)",
        // composition root and iOS umbrella framework
        ":shared:di -> :shared:(foundation|domain|pipeline|presentation|api-contract|data:local|data:remote|data:sync)",
        ":shared:ios-export -> :shared:(foundation|domain|presentation|di)",
        ":shared:testing -> :shared:(foundation|domain)",
        // Android (api-contract and data:* are reachable only through :shared:di)
        ":apps:android:feature-.* -> :shared:(presentation|domain|foundation)",
        ":apps:android:feature-.* -> :apps:android:designsystem",
        ":apps:android:platform -> :shared:(domain|foundation)",
        ":apps:android:app -> .*",
        // Server: shares DTOs only
        ":backend:api -> :shared:api-contract",
    )
}
