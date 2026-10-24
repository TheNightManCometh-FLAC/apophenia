#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/coreclasses
javac -d build/coreclasses $(find core/src -name '*.java') core/tests/*.java
java -ea -cp build/coreclasses CoreSmoke
java -ea -cp build/coreclasses ResearchPrototypesTest
