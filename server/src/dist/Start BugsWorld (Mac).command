#!/bin/sh
# Starts BugsWorld. Keep this window open while you use it.
cd "$(dirname "$0")" || exit 1
exec sh ./bin/bugsworld
