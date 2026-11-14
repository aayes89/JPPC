#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"
OUT=${1:-build-bootanim-preview}
CLASSES="$OUT/classes"
mkdir -p "$CLASSES"
find . -path './nandDump' -prune -o -path './build' -prune -o -path './build-*' -prune -o -name '*.java' -print > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$CLASSES" @"$OUT/sources.txt"
java -Djava.awt.headless=true -cp "$CLASSES" pcp.xenon.BootAnimationPreview "$ROOT" "$OUT"
