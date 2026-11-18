#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$(dirname "$0")/run-xenon-fb-smoke.sh"
./TESTS/run-stack-smoke.sh
./TESTS/run-bootanim-preview-smoke.sh
./TESTS/run-xam-bootanim-dispatch-smoke.sh
./TESTS/run-xam-entry-smoke.sh
./TESTS/run-bootanim-audio-smoke.sh

"$(dirname "$0")/run-bootanim-screen-smoke.sh"
