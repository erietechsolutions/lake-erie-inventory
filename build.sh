#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# build.sh  –  Compile and optionally run the IT Inventory Management System
#
# Usage:
#   ./build.sh          # compile only
#   ./build.sh run      # compile + launch interactive CLI
#   ./build.sh test     # compile + run test suite
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SRC_DIR="src/main/java"
OUT_DIR="out"
MAIN_CLASS="com.itinventory.Main"
TEST_CLASS="com.itinventory.InventoryTests"

echo "── Compiling ────────────────────────────────────────────────"
mkdir -p "$OUT_DIR"
find "$SRC_DIR" -name "*.java" | xargs javac -d "$OUT_DIR" -source 17 -target 17
echo "   Compilation successful."

case "${1:-}" in
  run)
    echo "── Launching CLI ────────────────────────────────────────────"
    java -cp "$OUT_DIR" "$MAIN_CLASS"
    ;;
  test)
    echo "── Running Tests ────────────────────────────────────────────"
    java -cp "$OUT_DIR" "$TEST_CLASS"
    ;;
  *)
    echo "   Build complete. Run with:  ./build.sh run | test"
    ;;
esac
