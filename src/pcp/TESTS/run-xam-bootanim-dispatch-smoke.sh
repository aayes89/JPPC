#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
rm -rf build
mkdir -p build
find . -name '*.java' -not -path './nandDump/*' -not -path './build/*' -print0 \
  | xargs -0 javac -encoding UTF-8 -d build
OUT=$(java -Djava.awt.headless=true -cp build pcp.PCP --xam --headless --trace --max 100000 --png build/xam-bootanim-dispatch.png 2>&1)
printf '%s\n' "$OUT" | grep -F '[TKERNEL] BOOTANIM REGISTERED base=98000000'
printf '%s\n' "$OUT" | grep -F '[TKERNEL] AniStartBootAnimation accepted:'
printf '%s\n' "$OUT" | grep -F '[TKERNEL] DISPATCH ordinal=678 AniStartBootAnimation PC=81A72348 LR=9FF00010'
printf '%s\n' "$OUT" | grep -F 'PC=81696af0 insn='
printf '%s\n' "$OUT" | grep -F "Halt: 'b .' en PC=0x9ffc0010"
printf '%s\n' "$OUT" | grep -F '[headless] 213 instrucciones, PC=0x9ffc0010, halted=true'
if printf '%s\n' "$OUT" | grep -F 'bootanim guest entry executed'; then
  echo 'ERROR: no debe afirmarse ejecución guest de bootanim sin entrypoint' >&2
  exit 1
fi
echo 'XAM_BOOTANIM_DISPATCH_SMOKE_OK: PPC32 sc dispatch -> XAM import ordinal 678 -> XAM entry -> thread exit; bootanim registered as kernel-managed (no PE entrypoint).'
