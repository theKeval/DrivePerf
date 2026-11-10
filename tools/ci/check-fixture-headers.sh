#!/usr/bin/env bash
# Rejects GPX/CSV files without a privacy header (ADR-008, "No real location data in the repo").
#
# Every tracked *.gpx and *.csv must declare in its first 5 lines that its coordinates are synthetic or were
# privacy-trimmed (start and end removed, coordinates shifted) before commit:
#   CSV:  a line starting with `# privacy-trimmed`
#   GPX:  an XML comment such as `<!-- privacy-trimmed: synthetic city loop -->`
# Usage: tools/ci/check-fixture-headers.sh [repo-root]   (default: current directory)
set -euo pipefail

root="${1:-.}"
header='^[[:space:]]*(#|<!--[[:space:]]*#?)[[:space:]]*privacy-trimmed'
failures=0
checked=0

while IFS= read -r -d '' file; do
    checked=$((checked + 1))
    if ! head -n 5 "$root/$file" | grep -Eqi "$header"; then
        echo "::error file=$file::GPX/CSV file has no '# privacy-trimmed' header in its first 5 lines (ADR-008)"
        failures=$((failures + 1))
    fi
done < <(git -C "$root" ls-files -z -- '*.gpx' '*.GPX' '*.csv' '*.CSV')

echo "Checked $checked GPX/CSV file(s), $failures without a privacy header."
[ "$failures" -eq 0 ]
