#!/bin/zsh
# Screenshot a prototype screen with headless Chrome.
# usage: prototypes/tools/shot.sh <html-file> <screen-id-or-""> <out.png> <css-width> <css-height> [scale=3]
set -e
file=${1:A}; hash=$2; out=${3:A}; w=$4; h=$5; scale=${6:-3}
url="file://$file"; [[ -n $hash ]] && url="$url#$hash"
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars \
  --screenshot="$out" --window-size="$w,$h" --force-device-scale-factor="$scale" \
  --virtual-time-budget=4000 "$url" >/dev/null 2>&1
[[ -s $out ]] && echo "$out" || { echo "screenshot failed: $url" >&2; exit 1; }
