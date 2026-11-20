import java.util.Properties

plugins {
    id("driveperf.android-application")
}

// Crash reporting DSN (ADR-007): injected at build time, never committed. Pass `-Pdriveperf.sentryDsn=...` or set
// DRIVEPERF_SENTRY_DSN. Blank means the Sentry account is not set up and crash reporting is a no-op stub.
val sentryDsn: Provider<String> = providers.gradleProperty("driveperf.sentryDsn")
    .orElse(providers.environmentVariable("DRIVEPERF_SENTRY_DSN"))
    .orElse("")

// Sentry R8 mapping ID (DRIVE-196, ADR-007): release-android.yml generates a UUID, this build writes it into the release
// variants' `sentry-debug-meta.properties` asset (sentry-android reads it to match stack traces with the mapping), and
// the lane uploads mapping.txt under the same UUID with sentry-cli. Without the variable nothing is added. No Sentry
// Gradle plugin: applied here it loads the Kotlin Gradle plugin a second time.
val sentryProguardUuid: String? = providers.environmentVariable("DRIVEPERF_SENTRY_PROGUARD_UUID").orNull
    ?.takeIf { it.isNotBlank() }
    ?.also { require(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(it)) { "DRIVEPERF_SENTRY_PROGUARD_UUID is not a UUID" } }

/** Writes `sentry-debug-meta.properties` with the R8 mapping's UUID. */
abstract class SentryDebugMetaTask : DefaultTask() {
    @get:Input
    abstract val proguardUuid: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        outputDirectory.get().file("sentry-debug-meta.properties").asFile
            .writeText("io.sentry.ProguardUuids=${proguardUuid.get()}\n")
    }
}

// Google Maps (ADR-005, DRIVE-200): the restricted API key and the optional Night Drive Map ID come from local.properties
// or the environment, never the repository (DRIVE-216 created the key: release-runbook.md Part G). Without a key Replay
// draws on the plain canvas.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun mapsSetting(name: String): String = localProperties.getProperty(name) ?: providers.environmentVariable(name).orNull ?: ""

val mapsApiKey = mapsSetting("MAPS_API_KEY")
val mapsMapId = mapsSetting("MAPS_MAP_ID")

// Release lane (DRIVE-218, ADR-008): release-android.yml sets the marketing version from the tag and the Play upload key
// from the `prod` environment. Without the keystore the release bundle is unsigned, which is what local builds and pull
// requests get. Play App Signing re-signs what users install. Steps: docs/launch/release-runbook.md.
val releaseVersionName: String = providers.environmentVariable("DRIVEPERF_VERSION_NAME").orElse("0.1.0").get()
val uploadKeystore: String? = providers.environmentVariable("DRIVEPERF_UPLOAD_KEYSTORE").orNull?.takeIf { it.isNotBlank() }

fun uploadSetting(name: String): String = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    ?: throw GradleException("$name is not set: the release lane signs with the upload key (docs/launch/release-runbook.md)")

android {
    namespace = "com.driveperf.app"
    compileSdk = 37 // libraries need the newest APIs to compile; targetSdk stays 36 (ADR-001)

    defaultConfig {
        applicationId = "com.driveperf.app"
        minSdk = 29
        targetSdk = 36
        // Build number = CI run number (ADR-008); the marketing version is set at release time.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = releaseVersionName
        buildConfigField("String", "SENTRY_DSN", "\"${sentryDsn.get()}\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_KEY", mapsApiKey.isNotBlank().toString())
        buildConfigField("String", "MAPS_MAP_ID", "\"$mapsMapId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Environments (ADR-008): `dev` installs next to `prod` with its own application ID.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
        }
        create("prod") {
            dimension = "environment"
        }
    }

    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = uploadSetting("DRIVEPERF_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadSetting("DRIVEPERF_UPLOAD_KEY_ALIAS")
                keyPassword = uploadSetting("DRIVEPERF_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric Compose UI tests
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // ADR-001: targetSdk 36 on purpose; it moves to 37 when Play requires it, after a compatibility pass.
        disable += "OldTargetApi"
    }
}

dependencies {
    // The app is the composition root: it may depend on everything (ADR-002 section 2).
    implementation(projects.shared.foundation)
    implementation(projects.shared.domain)
    implementation(projects.shared.presentation)
    implementation(projects.shared.di)
    implementation(projects.apps.android.designsystem)
    implementation(projects.apps.android.platform)
    implementation(projects.apps.android.featureCapture)
    implementation(projects.apps.android.featureDrives)
    implementation(projects.apps.android.featureReplay)
    implementation(projects.apps.android.featureRoutes)
    implementation(projects.apps.android.featureInsights)
    implementation(projects.apps.android.featureGarage)
    implementation(projects.apps.android.featureAccount)
    implementation(projects.apps.android.featurePrivacy)
    implementation(projects.apps.android.featurePaywall)
    implementation(projects.apps.android.featureSettings)
    implementation(projects.apps.android.featureOnboarding)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val uuid = sentryProguardUuid ?: return@onVariants
        val debugMeta = tasks.register<SentryDebugMetaTask>("sentryDebugMeta${variant.name.replaceFirstChar(Char::uppercase)}") {
            proguardUuid.set(uuid)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(debugMeta, SentryDebugMetaTask::outputDirectory)
    }
}

// android.yml runs `:apps:android:app:detekt` and `:apps:android:app:testDevDebugUnitTest` only. Hooking the library
// modules onto those two tasks keeps their checks and unit tests in CI without a workflow change.
val libraryModules = listOf("designsystem", "platform") + listOf(
    "capture", "drives", "replay", "routes", "insights", "garage", "account", "onboarding", "settings",
).map { "feature-$it" }
tasks.named("detekt") {
    dependsOn(libraryModules.map { ":apps:android:$it:detekt" })
}
tasks.matching { it.name == "testDevDebugUnitTest" }.configureEach {
    dependsOn(libraryModules.filter { it != "designsystem" }.map { ":apps:android:$it:testDebugUnitTest" })
}
