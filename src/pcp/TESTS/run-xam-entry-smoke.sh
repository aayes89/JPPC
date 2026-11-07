#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build-xam-smoke"
LOG="$OUT/xam-entry.log"
rm -rf "$OUT"
mkdir -p "$OUT"
find "$ROOT" -path "$ROOT/nandDump" -prune -o -path "$OUT" -prune -o -name '*.java' -print > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"
java -cp "$OUT/classes" pcp.PCP --xam --headless --max 100000 --png "$OUT/xam-entry.png" > "$LOG" 2>&1
cat "$LOG"
grep -F "[CPU] Halt: 'b .'" "$LOG" >/dev/null
 grep -F "halted=true" "$LOG" >/dev/null
if grep -E '\[CPU\] Excepci.n|sin manejador instalado|PC=0x0' "$LOG" >/dev/null; then
    echo "XAM_ENTRY_SMOKE_FAIL: guest entry returned through an exception" >&2
    exit 1
fi
echo "XAM_ENTRY_SMOKE_OK: PE mapped, Xenon thread frame initialized, entry point returned normally."
