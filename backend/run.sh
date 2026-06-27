#!/usr/bin/env bash
set -euo pipefail
mkdir -p out
if command -v node >/dev/null 2>&1; then
  node ../scripts/fetch-jdbc.mjs
else
  echo "[setup] Node.js not found. MockDB will work, but remote JDBC drivers may be missing."
fi
javac -cp "lib/*" -d out $(find src -name '*.java' | sort)
PORT="${PORT:-9090}" java -cp "out:lib/*" com.askdb.Main
