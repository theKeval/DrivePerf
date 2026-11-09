plugins {
    id("driveperf.jvm-library")
    id("driveperf.serialization")
    application
}

// Kotlin/Ktor server (ADR-002 section 6). Layers: routes -> application -> ports <- adapters.
// It shares DTOs with the apps through :shared:api-contract and depends on nothing else in shared/.
application {
    mainClass.set("com.driveperf.server.MainKt")
}

dependencies {
    implementation(projects.shared.apiContract)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}
