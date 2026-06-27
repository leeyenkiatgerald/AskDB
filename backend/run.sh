#!/usr/bin/env bash
set -euo pipefail
mkdir -p out
javac -d out $(find src -name '*.java' | sort)
PORT="${PORT:-9090}" java -cp out com.askdb.Main
