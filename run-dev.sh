#!/usr/bin/env bash
# Starts RuneLite with Leadman loaded as a built-in plugin.
#
# Jagex account login: see docs/DEVELOPING.md
# Uses the portable JDK in .tools/ if one is there, so no system-wide Java install
# is needed. If you already have JDK 11 on your PATH, `./gradlew run` is enough.
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ]; then
  JDK=$(ls -d .tools/jdk-11* 2>/dev/null | head -1 || true)
  if [ -z "$JDK" ]; then
    echo "No JDK found. Either install JDK 11, or download a portable one into .tools/:"
    echo "  curl -L -o jdk.zip 'https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse'"
    exit 1
  fi
  # Prefer the Windows-style path where pwd -W offers one: the JVM is a Windows
  # binary and does not understand an MSYS /d/... path. Braces matter here --
  # without them the || binds to the cd and both pwd forms run.
  export JAVA_HOME="$(cd "$JDK" && { pwd -W 2>/dev/null || pwd; })"
fi

echo "JAVA_HOME=$JAVA_HOME"
exec ./gradlew run "$@"
