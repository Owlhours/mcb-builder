#!/usr/bin/env bash
# Runs the unit tests: compiles the code under test and the tests, then runs them with the JUnit console launcher
# (downloaded into .deps/ by tools/deps.sh on the first run, checked against its pinned SHA-1).
#
#   tools/test.sh                    all suites
#   tools/test.sh schem              only these suites
#
# Each suite is "name|source roots|test roots" (roots separated by spaces). The tests need no Minecraft: a suite's
# sources must compile on their own, with nothing but a JDK and fastutil (the planner's one library). To add tests, add a suite here or put more test classes
# (names ending in Test) under an existing test root.
#
# Needs: bash, curl, sha1sum, and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")/.."
SUITES=(
    "schem|core/src/mcb/schem|core/test/mcb/schem"
    "core|core/src core/examples|core/test/mcb/build core/test/mcb/bot core/test/mcb/game"
)
. tools/deps.sh
junit_cp
fastutil_cp
W=build/test; rm -rf "$W"; mkdir -p "$W"
ran=0
for suite in "${SUITES[@]}"; do
    IFS='|' read -r name src tests <<< "$suite"
    if [ $# -gt 0 ] && [[ " $* " != *" $name "* ]]; then continue; fi
    echo "== $name"
    # shellcheck disable=SC2086
    compile "$W/$name/main" "$FASTUTIL_CP" $src
    # shellcheck disable=SC2086
    compile "$W/$name/test" "$W/$name/main:$FASTUTIL_CP:$JUNIT_CP" $tests
    java -jar "$JUNIT_CP" execute --disable-banner --details=summary --fail-if-no-tests \
        --class-path "$W/$name/main:$W/$name/test:$FASTUTIL_CP" --scan-class-path "$W/$name/test"
    ran=$((ran + 1))
done
[ "$ran" -gt 0 ] || { echo "no such suite: $*" >&2; exit 2; }
# the examples in core/examples must keep running (they are compiled with the core suite)
if [ -d "$W/core/main" ]; then
    for ex in NavExample JudgeExample; do
        java -cp "$W/core/main:$FASTUTIL_CP" "mcb.example.$ex" > "$W/$ex.out" || { echo "example $ex failed"; cat "$W/$ex.out"; exit 1; }
        echo "example $ex runs"
    done
fi
echo "tests: all $ran suite(s) passed"
