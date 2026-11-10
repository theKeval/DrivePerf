#!/usr/bin/env bash
# Proves tools/ci/check-fixture-headers.sh fires: it must accept trimmed fixtures and reject the rest.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git -C "$tmp" init -q

printf '# privacy-trimmed: synthetic\nt,lat,lon\n0,1,2\n' > "$tmp/good.csv"
printf '<?xml version="1.0"?>\n<!-- privacy-trimmed: synthetic city loop -->\n<gpx/>\n' > "$tmp/good.gpx"
git -C "$tmp" add . >/dev/null
"$here/check-fixture-headers.sh" "$tmp" >/dev/null || { echo "FAIL: rejected valid fixtures"; exit 1; }

printf 't,lat,lon\n0,43.65,-79.38\n' > "$tmp/real-drive.csv"
git -C "$tmp" add . >/dev/null
if "$here/check-fixture-headers.sh" "$tmp" >/dev/null 2>&1; then echo "FAIL: accepted a CSV without the header"; exit 1; fi
git -C "$tmp" rm -qf real-drive.csv

printf '<?xml version="1.0"?>\n<gpx><trk/></gpx>\n' > "$tmp/real-drive.gpx"
git -C "$tmp" add . >/dev/null
if "$here/check-fixture-headers.sh" "$tmp" >/dev/null 2>&1; then echo "FAIL: accepted a GPX without the header"; exit 1; fi

echo "OK: the fixture check accepts trimmed fixtures and rejects untrimmed CSV and GPX."
