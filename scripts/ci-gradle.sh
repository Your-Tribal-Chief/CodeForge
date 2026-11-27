#!/usr/bin/env bash
# Runs Gradle and turns compiler errors into GitHub annotations (visible without reading raw logs).
set -uo pipefail
gradle --no-daemon --stacktrace "$@" 2>&1 | tee -a gradle-ci.log
code=${PIPESTATUS[0]}
if [ "$code" -ne 0 ]; then
  # Kotlin: "e: file:///path/File.kt:12:5 message"
  grep -E '^e: ' gradle-ci.log | sed -E 's#^e: (file://)?##' | head -40 | while IFS= read -r line; do
    echo "::error::$line"
  done
  # javac / aapt / R8 / Gradle summary
  grep -E 'error:|AAPT: error|ERROR:|Could not (resolve|find)' gradle-ci.log | grep -v '^e: ' | head -20 | while IFS= read -r line; do
    echo "::error::$line"
  done
  awk '/What went wrong:/{f=1;n=0} f{print; if(++n>8) f=0}' gradle-ci.log | tr '\n' ' ' | sed 's/^/::error::/' ; echo
fi
exit "$code"
