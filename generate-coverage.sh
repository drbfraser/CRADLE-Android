#!/bin/sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

UNIT_SRC="$SCRIPT_DIR/app/build/reports/tests/testDebugUnitTest"
INSTR_SRC="$SCRIPT_DIR/app/build/reports/coverage/androidTest/debug/connected"
OUT="$SCRIPT_DIR/test-coverage"

if [ ! -d "$UNIT_SRC" ]; then
  echo "ERROR: Unit test report not found. Run: ./gradlew testDebugUnitTest"
  exit 1
fi

if [ ! -d "$INSTR_SRC" ]; then
  echo "ERROR: Instrumented test report not found. Run: ./gradlew createDebugCoverageReport"
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"

cp -r "$UNIT_SRC"  "$OUT/unit"
cp -r "$INSTR_SRC" "$OUT/instrumented"

echo "Reports copied:"
echo "Unit tests: test-coverage/unit/index.html"
echo "Instrumented tests: test-coverage/instrumented/index.html"
