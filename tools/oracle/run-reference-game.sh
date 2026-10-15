#!/usr/bin/env bash
# Runs the real BWServer engine headlessly and prints species counts.
# Usage: run-reference-game.sh rows cols bugsPerPlayer seconds "prog1" "prog2" ...
#   e.g. run-reference-game.sh 15 15 50 10 "16, 5, 1, 6, 0, 5" "16, 5, 3, 6, 0, 5"
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"

"$java_bin" -Djava.awt.headless=true -cp "$root/lib/BWServer.jar" \
    "$root/tools/oracle/ReferenceGame.java" "$@" \
    | grep -vE '^(Change to |[0-9]+ now |Bug .* infected )' 
