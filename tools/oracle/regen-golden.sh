#!/usr/bin/env bash
# Regenerates engine/src/test/resources/golden/**/*.bo and *.err from the
# .bl files there, using the OSU reference compiler in lib/BWClient.jar.
# Needs a JDK 21+ (`java` able to launch single-file source programs).
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
jar="$root/lib/BWClient.jar"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if [[ ! -f "$jar" ]]; then
    echo "missing $jar (the OSU jars are not committed; copy them into lib/)" >&2
    exit 1
fi

mapfile -t sources < <(find "$root/engine/src/test/resources/golden" -name '*.bl' | sort)
"$java_bin" -Djava.awt.headless=true -cp "$jar" \
    "$root/tools/oracle/ReferenceCompile.java" "${sources[@]}"
