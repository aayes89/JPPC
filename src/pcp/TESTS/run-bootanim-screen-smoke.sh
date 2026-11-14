#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build-bootanim-screen-smoke"
rm -rf "$OUT"
mkdir -p "$OUT/classes"
find "$ROOT" -path "$ROOT/nandDump" -prune -o -path "$OUT" -prune -o -path "$ROOT/.git" -prune -o -name '*.java' -print > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"
java -Djava.awt.headless=true -cp "$OUT/classes" pcp.xenon.BootAnimationScreenSmoke \
  "$ROOT/nandDump/extracted/resources/xam.xex/xam/xenonLogo.png" \
  "$OUT/xenon-bootanim-screen.png"
