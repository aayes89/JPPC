#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build-bootanim-preview-smoke"
rm -rf "$OUT"
mkdir -p "$OUT"
find "$ROOT" -path "$ROOT/nandDump" -prune -o -path "$ROOT/build" -prune -o -path "$ROOT/build-*" -prune -o -name '*.java' -print > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"
java -Djava.awt.headless=true -cp "$OUT/classes" pcp.xenon.BootAnimationPreview "$ROOT" "$OUT/output" > "$OUT/run.log"
test -s "$OUT/output/xenon-bootanim-host-preview.gif"
test -s "$OUT/output/xenon-bootanim-first-frame.png"
cmp "$ROOT/nandDump/extracted/resources/bootanim.xex/audio.wav" "$OUT/output/bootanim-original.wav"
grep -F 'bootanim.xex guest code NOT executed' "$OUT/run.log" >/dev/null
echo 'BOOTANIM_PREVIEW_SMOKE_OK: JDK GIF/PNG output generated; NAND WAV preserved byte-for-byte; guest execution explicitly bypassed.'
