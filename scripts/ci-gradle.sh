#!/usr/bin/env bash
# Runs Gradle and turns failures into GitHub annotations (visible via the API without raw logs).
set -uo pipefail
gradle --no-daemon --stacktrace "$@" 2>&1 | tee -a gradle-ci.log
code=${PIPESTATUS[0]}
enc() { python3 -c 'import sys; print(sys.stdin.read()[:60000].replace("%","%25").replace("\r","").replace("\n","%0A"))'; }
if [ "$code" -ne 0 ]; then
  k="$(grep -E '^e: ' gradle-ci.log | sed -E 's#^e: (file://)?[^ ]*/(app|terminal-[a-z]+)/src/main/java/#\2/#' | head -60)"
  [ -n "$k" ] && echo "::error title=Kotlin errors::$(printf '%s\n' "$k" | enc)"
  j="$(grep -E ': error: |AAPT: error|ERROR:' gradle-ci.log | head -40)"
  [ -n "$j" ] && echo "::error title=Other errors::$(printf '%s\n' "$j" | enc)"
  w="$(awk '/What went wrong:/{f=1} /Try:/{f=0} f' gradle-ci.log | head -80)"
  [ -n "$w" ] && echo "::error title=What went wrong::$(printf '%s\n' "$w" | enc)"
  c="$(grep -E 'Caused by:' gradle-ci.log | sort -u | head -15)"
  [ -n "$c" ] && echo "::error title=Causes::$(printf '%s\n' "$c" | enc)"
fi
exit "$code"
