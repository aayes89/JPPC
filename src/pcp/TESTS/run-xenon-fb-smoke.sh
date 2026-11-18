#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build-xenon-fb-smoke"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
find "$ROOT" -path "$ROOT/nandDump" -prune -o -path "$OUT" -prune -o -name '*.java' -print > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"
java -Djava.awt.headless=true -cp "$OUT/classes" pcp.TESTS.XenonFramebufferVirtualMmioSelfTest
