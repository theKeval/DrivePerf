#!/bin/sh
# Xcode "Build Kotlin framework" Run Script phase (ADR-002 section 8): builds and embeds the static framework
# "DrivePerfShared" from :shared:ios-export with the repository's Gradle wrapper. Xcode provides CONFIGURATION,
# SDK_NAME, ARCHS and the target paths that the Kotlin Gradle plugin reads.
set -eu

if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" = "YES" ]; then
    echo "Skipping the Gradle build: the Kotlin IDE plugin builds the framework."
    exit 0
fi

cd "$SRCROOT/../.."

# Xcode's build environment has no JAVA_HOME; any JDK 17+ can launch Gradle (the build uses a JDK 21 toolchain).
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
    export JAVA_HOME
fi

./gradlew --console=plain :shared:ios-export:embedAndSignAppleFrameworkForXcode
