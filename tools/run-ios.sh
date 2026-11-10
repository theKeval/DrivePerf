#!/usr/bin/env bash
# Build the iOS app for the simulator (no code signing), install it and launch it. One command:
#   tools/run-ios.sh [simulator name or UDID]      default: the first available iPhone
# Needs Xcode 26+ with an iOS simulator runtime and a JDK 17+ (Gradle builds the Kotlin framework).
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
derived="$repo/build/DerivedData"
want="${1:-}"

if [ -n "$want" ]; then
    udid="$(xcrun simctl list devices available -j | jq -r --arg w "$want" '[.devices[][] | select(.udid == $w or .name == $w)][0].udid // empty')"
else
    udid="$(xcrun simctl list devices available iPhone -j | jq -r '[.devices[][] | select(.name | startswith("iPhone"))][0].udid // empty')"
fi
if [ -z "$udid" ]; then
    echo "No iPhone simulator found. Install an iOS runtime first: xcodebuild -downloadPlatform iOS" >&2
    exit 1
fi

xcrun simctl boot "$udid" 2>/dev/null || true
xcrun simctl bootstatus "$udid" -b >/dev/null
open -a Simulator

xcodebuild -project "$repo/apps/ios/DrivePerf.xcodeproj" -scheme DrivePerf \
    -destination "id=$udid" -derivedDataPath "$derived" build CODE_SIGNING_ALLOWED=NO -quiet

xcrun simctl install "$udid" "$derived/Build/Products/Debug-iphonesimulator/DrivePerf.app"
xcrun simctl launch "$udid" com.driveperf.app.dev
