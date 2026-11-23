#!/usr/bin/env bash
# The client mod's planner check: the client mod used to have its own copy of the planner (client/src/mcb, last
# at commit 0ff6f68). This runs that old copy and the shared planner of the working tree side by side, both with
# the client mod's settings, in the headless game (mcb.headless.ClientRun: fall damage on, registered containers read
# by walking to them, the grid read from the world), and requires identical rows: ticks, counts, verdict, and the
# trace of every key, click and position, tick by tick.
#
#   tools/client-check.sh                  the generated test builds (gate/src/mcb/gate/Synth.java)
#   tools/client-check.sh a.mcb b.mcb ...  normalised builds as well (the harness's corpus format)
#   J=4 tools/client-check.sh ...          worker processes (default: the number of cores, at most 8)
#
# Then it checks two things the old client did wrong: a floating build (a file whose bottom layers are empty) must
# land at its height above the file's origin (mcb.headless.ClientFloatCheck); and a bot that runs out of temporary
# blocks must ask its owner for more, and finish once given them (mcb.headless.ClientShortageCheck).
#
# The old side is the old client planner (client/src/mcb/{bot,build,schem} at 0ff6f68, with one mechanical change:
# its status field "c.note = x" became the call "c.note(x)") behind the headless runner and the server-side body as
# they were when this check was written (the commit that added ClientGate.java). The new side is the working tree.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
. tools/deps.sh
server_cp
W=$ROOT/build/client-check; rm -rf "$W"; mkdir -p "$W"
OLD_PLANNER=0ff6f68
ENV_AT=$(git log --diff-filter=A --format=%H -- adapter/headless/src/mcb/headless/ClientGate.java | tail -1)
[ -n "$ENV_AT" ] || { echo "client-check: ClientGate.java is not committed yet" >&2; exit 1; }
# ---- the old side
mkdir -p "$W/old"
git archive "$OLD_PLANNER" client/src/mcb | tar -x -C "$W/old"
rm -rf "$W/old/client/src/mcb/env"                                   # (the client's own body; the headless one stands in)
grep -rl 'c\.note = ' "$W/old/client/src/mcb" | xargs sed -i -E 's/c\.note = ([^;]*);/c.note(\1);/g'
git archive "$ENV_AT" adapter/src/mcb/env adapter/headless/src/mcb/headless/ClientRun.java adapter/headless/src/mcb/headless/ClientGate.java gate/src | tar -x -C "$W/old"
rm -f "$W/old/adapter/src/mcb/env/Depot.java"                         # (needs the shared Site; not used by ClientRun)
mkdir -p "$W/old/shim/mcb/headless"
cat > "$W/old/shim/mcb/headless/ClientPlanner.java" <<'EOF'
package mcb.headless;
/** The old client planner: its fall limit was a static, set before the run. */
final class ClientPlanner {
    static mcb.bot.Builder builder(mcb.env.Client c, mcb.bot.Grid g, mcb.build.Site site, mcb.env.Oracle oracle, int maxFall16) {
        mcb.bot.Nav.MAX_FALL16 = maxFall16;
        return new mcb.bot.Builder(c, g, site, oracle);
    }
}
EOF
compile "$W/old/classes" "$SERVER_CP" "$W/old/client/src" "$W/old/adapter/src" "$W/old/adapter/headless/src" "$W/old/shim" "$W/old/gate/src"
# ---- the new side
compile "$W/new/classes" "$SERVER_CP" core/src adapter/src adapter/server/src adapter/headless/src gate/src $(version_src server)
# ---- run both
J=${J:-$(nproc 2>/dev/null || echo 2)}; [ "$J" -gt 8 ] && J=8
jobs=()
for n in $(java -cp "$W/new/classes:$SERVER_CP" mcb.headless.Gate list 2> /dev/null | grep -v '^\[' | cut -f1); do jobs+=("synth $n"); done
for f in "$@"; do jobs+=("mcb $(cd "$(dirname "$f")" && pwd)/$(basename "$f")"); done
printf '%s\n' "${jobs[@]}" > "$W/jobs"
run() {  # side job...
    local side=$1; shift
    local d; d=$(mktemp -d "$W/$side/run.XXXXXX")
    (cd "$d" && java -Xmx3g -XX:+UseSerialGC -Djava.io.tmpdir="$d" -cp "$W/$side/classes:$SERVER_CP" mcb.headless.ClientGate "$@" 2> /dev/null | grep -v '^\[') >> "$W/$side.rows"
    rm -rf "$d"
}
export -f run; export W SERVER_CP
for side in old new; do
    xargs -P "$J" -L 1 bash -c 'run '"$side"' "$@"' _ < "$W/jobs"
    sort "$W/$side.rows" -o "$W/$side.rows"
done
n=$(wc -l < "$W/jobs")
[ "$(wc -l < "$W/old.rows")" -eq "$n" ] && [ "$(wc -l < "$W/new.rows")" -eq "$n" ] || { echo "client-check: missing rows"; exit 1; }
if diff "$W/old.rows" "$W/new.rows" > "$W/diff.txt"; then
    echo "client-check: PASS, the shared planner in client mode played the old client planner's course on $n of $n builds"
else
    echo "client-check: FAIL (< old client planner, > shared planner):"; cat "$W/diff.txt"; exit 1
fi
# ---- and the client's placement of a floating build (empty bottom layers kept, so it lands where the file says)
d=$(mktemp -d "$W/new/float.XXXXXX")
(cd "$d" && java -Xmx3g -XX:+UseSerialGC -cp "$W/new/classes:$SERVER_CP" mcb.headless.ClientFloatCheck 2> /dev/null | grep '^float check') || { echo "client-check: the floating build check FAILED"; exit 1; }
# ---- and the client running out of temporary blocks: it asks the owner, and finishes once given more
d=$(mktemp -d "$W/new/short.XXXXXX")
(cd "$d" && java -Xmx3g -XX:+UseSerialGC -cp "$W/new/classes:$SERVER_CP" mcb.headless.ClientShortageCheck 2> /dev/null | grep '^shortage check') || { echo "client-check: the shortage check FAILED"; exit 1; }
