#!/usr/bin/env bash
# The regression gate: builds every generated test build (gate/src/mcb/gate/Synth.java) in the headless game and
# compares each result row with gate/expected/<mc>.tsv. Any difference fails: the bot is deterministic, so the same
# code gives the same ticks and counts, and a change that alters what the bot does shows up here.
#
#   gate/gate.sh                 run all test builds, compare (exit 1 on any difference)
#   gate/gate.sh hut tower       only these
#   gate/gate.sh --record        run all and write gate/expected/<mc>.tsv (after a deliberate change of behaviour)
#   J=4 gate/gate.sh             worker processes (default: the number of cores, at most 8)
#   MC=26.3 gate/gate.sh         another Minecraft version (its own expected rows)
#
# Needs a JDK 25, bash, curl, unzip and sha1sum; the first run downloads the vanilla server jar (tools/deps.sh).
set -euo pipefail
cd "$(dirname "$0")/.."
RECORD=
if [ "${1:-}" = "--record" ]; then RECORD=1; shift; fi
. tools/deps.sh
server_cp
OUT=$PWD/build/headless
rm -rf "$OUT"; mkdir -p "$OUT"
compile "$OUT" "$SERVER_CP" core/src adapter/src adapter/server/src adapter/headless/src gate/src $(version_src server)
CP="$OUT:$SERVER_CP"
EXPECTED=gate/expected/$MC.tsv
W=build/gate; rm -rf "$W"; mkdir -p "$W"
if [ $# -gt 0 ]; then printf '%s\n' "$@" > "$W/names"
else java -cp "$CP" mcb.headless.Gate list 2> /dev/null | grep -v '^\[' | sort -t$'\t' -k3,3nr | cut -f1 > "$W/names"; fi
J=${J:-$(nproc 2>/dev/null || echo 2)}; [ "$J" -gt 8 ] && J=8
n=$(wc -l < "$W/names"); [ "$J" -gt "$n" ] && J=$n
# deal the builds out largest first, one share per worker process
for i in $(seq 0 $((J - 1))); do awk -v i="$i" -v j="$J" 'NR % j == i' "$W/names" > "$W/share$i"; done
for i in $(seq 0 $((J - 1))); do
    ( cd "$W" && mkdir -p "w$i" && cd "w$i" && \
      java ${JAVA_OPTS:--Xmx2g -XX:+UseSerialGC} -Djava.io.tmpdir="$PWD" -cp "$CP" mcb.headless.Gate run $(cat "../share$i") > out.txt 2> err.txt ) &
done
wait
cat "$W"/w*/out.txt | awk -F'\t' 'NF == 16 && $1 != "name"' | sort > "$W/rows.tsv"
got=$(wc -l < "$W/rows.tsv")
[ "$got" -eq "$n" ] || { echo "gate: $got rows for $n builds (see $W/w*/err.txt)"; exit 1; }
if [ -n "$RECORD" ]; then
    mkdir -p gate/expected
    { echo "# Expected gate rows for Minecraft $MC, recorded with gate/gate.sh --record. Columns: see mcb.headless.Gate."; cat "$W/rows.tsv"; } > "$EXPECTED"
    echo "gate: recorded $n rows in $EXPECTED"; exit 0
fi
[ -f "$EXPECTED" ] || { echo "gate: no $EXPECTED (record it with --record)"; exit 1; }
grep -v '^#' "$EXPECTED" | sort > "$W/expected.tsv"
if [ $# -gt 0 ]; then
    awk -F'\t' 'NR == FNR { want[$1] = 1; next } want[$1]' <(sort "$W/names") "$W/expected.tsv" > "$W/expected.sel"; mv "$W/expected.sel" "$W/expected.tsv"
fi
if diff <(cut -f1-16 "$W/expected.tsv") "$W/rows.tsv" > "$W/diff.txt"; then
    echo "gate: PASS, $n of $n test builds as expected ($(awk -F'\t' '$3 == "PASS"' "$W/rows.tsv" | wc -l) built to a judge PASS)"
else
    echo "gate: FAIL, results differ from $EXPECTED (< expected, > now):"; cat "$W/diff.txt"; exit 1
fi
