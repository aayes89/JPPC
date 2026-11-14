#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/build-bootanim-audio-smoke"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
find "$ROOT" -path "$ROOT/nandDump" -prune -o -path "$OUT" -prune -o -name '*.java' -print > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"
java -Djava.awt.headless=true -cp "$OUT/classes" pcp.xenon.BootAnimationAudioSmoke \
  "$ROOT/nandDump/extracted/resources/bootanim.xex/audio-pcm.wav"
