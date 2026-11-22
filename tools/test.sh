#!/usr/bin/env bash
# Runs the unit tests: compiles the code under test and the tests, then runs them with the JUnit console launcher
# (downloaded into .deps/ by tools/deps.sh on the first run, checked against its pinned SHA-1).
#
#   tools/test.sh                    all suites
#   tools/test.sh schem              only these suites
#
# Each suite is "name|source roots|test roots" (roots separated by spaces). The tests need no Minecraft: a suite's
# sources must compile on their own, with nothing but a JDK. To add tests, add a suite here or put more test classes
# (names ending in Test) under an existing test root.
#
# Needs: bash, curl, sha1sum, and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")/.."
SUITES=(
    "schem|core/src/mcb/schem|core/test/mcb/schem"
)
. tools/deps.sh
junit_cp
W=build/test; rm -rf "$W"; mkdir -p "$W"
ran=0
for suite in "${SUITES[@]}"; do
    IFS='|' read -r name src tests <<< "$suite"
    if [ $# -gt 0 ] && [[ " $* " != *" $name "* ]]; then continue; fi
    echo "== $name"
    # shellcheck disable=SC2086
    compile "$W/$name/main" "" $src
    # shellcheck disable=SC2086
    compile "$W/$name/test" "$W/$name/main:$JUNIT_CP" $tests
    java -jar "$JUNIT_CP" execute --disable-banner --details=summary --fail-if-no-tests \
        --class-path "$W/$name/main:$W/$name/test" --scan-class-path "$W/$name/test"
    ran=$((ran + 1))
done
[ "$ran" -gt 0 ] || { echo "no such suite: $*" >&2; exit 2; }
echo "tests: all $ran suite(s) passed"
