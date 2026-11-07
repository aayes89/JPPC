#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build-test"
rm -rf "$OUT"
mkdir -p "$OUT"
find "$ROOT" -path "$ROOT/nandDump" -prune -o -name '*.java' -print > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$OUT" @"$OUT/sources.txt"
java -cp "$OUT" tests.StackAliasSmoke
