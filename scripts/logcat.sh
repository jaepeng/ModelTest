#!/usr/bin/env bash
# Usage:
#   ./logcat.sh              # live tail, all app logs
#   ./logcat.sh crash        # only crash/fatal/error
#   ./logcat.sh native       # only native crashes (signal/tombstone/DEBUG)
#   ./logcat.sh app          # only our app package logs
#   ./logcat.sh dump         # dump current buffer once, then exit
set -euo pipefail

PKG="com.example.modeltest"
FILTERS="LLAMA_JNI:V LlmService:V HomeViewModel:V ChallengeParser:V AndroidRuntime:E DEBUG:V libc:V MIUIScout:V Choreographer:V *:S"

case "${1:-live}" in
  crash)
    adb logcat -c 2>/dev/null || true
    echo "[logcat] watching crashes... (Ctrl+C to stop)"
    adb logcat "*:E" | grep --line-buffered -iE "$PKG|fatal|signal|tomb|abort|exception"
    ;;
  native)
    adb logcat -c 2>/dev/null || true
    echo "[logcat] watching native crashes... (Ctrl+C to stop)"
    adb logcat | grep --line-buffered -iE "signal 11|signal 6|signal 4|tombstone|DEBUG :|abort\(\)|backtrace:"
    ;;
  app)
    adb logcat -c 2>/dev/null || true
    echo "[logcat] watching $PKG logs... (Ctrl+C to stop)"
    adb logcat $FILTERS | grep --line-buffered "$PKG"
    ;;
  dump)
    echo "[logcat] dumping current buffer..."
    adb logcat -d $FILTERS | grep "$PKG"
    ;;
  live|*)
    adb logcat -c 2>/dev/null || true
    echo "[logcat] live tail (Ctrl+C to stop)"
    adb logcat $FILTERS | grep --line-buffered "$PKG"
    ;;
esac
