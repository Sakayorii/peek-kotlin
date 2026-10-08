#!/bin/bash
# Verifies the Kotlin peek port against peek-vanilla's golden corpus.
#   ./verify.sh [regen]   — pass "regen" to regenerate the corpus with node first.
set -e
cd "$(dirname "$0")"

export PATH="$HOME/workspace/toolchain/jre/bin:$PATH"
KOTLINC="$HOME/workspace/toolchain/kotlinc/bin/kotlinc"
JUNIT="$HOME/workspace/toolchain/junit-4.13.2.jar"
HAMCREST="$HOME/workspace/toolchain/hamcrest-core-1.3.jar"
STDLIB="$HOME/workspace/toolchain/kotlinc/lib/kotlin-stdlib.jar"

if [ ! -x "$KOTLINC" ]; then
  echo "kotlinc not found at $KOTLINC — toolchain still downloading?"
  exit 1
fi

if [ "$1" = "regen" ]; then
  echo "== regenerating golden corpus with node =="
  node gen-corpus.mjs
fi

echo "== compiling core + tests =="
rm -rf build && mkdir -p build/classes build/test-classes
"$KOTLINC" src/main/kotlin -d build/classes 2>&1 | grep -v "^warning:" || true
"$KOTLINC" src/test/kotlin -cp "build/classes:$JUNIT:$HAMCREST:$STDLIB" -d build/test-classes 2>&1 | grep -v "^warning:" || true

echo "== running golden tests =="
java -cp "build/classes:build/test-classes:src/test/resources:$JUNIT:$HAMCREST:$STDLIB" \
  org.junit.runner.JUnitCore com.sakayori.peek.PeekGoldenTest com.sakayori.peek.PeekAnimateTest
