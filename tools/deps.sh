# Sourced by the build scripts: downloads what the mods compile against into a local .deps/ folder (never committed),
# checks each file against a pinned SHA-1, and sets classpath variables. Nothing downloaded here is redistributed.
#
#   . tools/deps.sh            (from the repository root, after setting MC, default 26.2)
#   server_cp                  -> $SERVER_CP: the vanilla dedicated server jar and its bundled libraries
#   fabric_cp                  -> $FABRIC_CP: Fabric Loader and Sponge Mixin
#   client_cp                  -> $CLIENT_CP: the vanilla client jar and its libraries, plus Fabric API modules
#   fastutil_cp                -> $FASTUTIL_CP: fastutil, the planner's only library (core/ without the game)
#   junit_cp                   -> $JUNIT_CP: the JUnit console launcher (tests only)
#
#   version_src side...        -> prints the version's source overlays for these sides (see below)
#
# The pins for each Minecraft version are in adapter/versions/<version>.env. Code that differs between versions lives
# in small source overlays, adapter/versions/<version>/src (code both mods share) and
# adapter/versions/<version>/<side>/src (side: server, client), each version holding the same class names; the build
# scripts add the overlays of $MC to their source roots (adapter/versions/README.md).
# Needs: bash, curl, unzip (or a JDK's jar), sha1sum, python3 (client only).

MC=${MC:-26.2}
ROOT=${ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}
DEPS=${DEPS:-$ROOT/.deps}
VERSION_ENV="$ROOT/adapter/versions/$MC.env"
[ -f "$VERSION_ENV" ] || { echo "no pins for Minecraft $MC ($VERSION_ENV)" >&2; exit 1; }
# shellcheck disable=SC1090
. "$VERSION_ENV"

fetch() {  # url file sha1
    if [ ! -f "$2" ] || ! echo "$3  $2" | sha1sum -c --status; then
        echo "downloading $(basename "$2")" >&2
        mkdir -p "$(dirname "$2")"
        curl -fsSL -o "$2.part" "$1"
        echo "$3  $2.part" | sha1sum -c --status || { echo "SHA-1 mismatch for $1" >&2; exit 1; }
        mv "$2.part" "$2"
    fi
}

unpack() {  # jar dir paths...
    local jar=$1 dir=$2; shift 2
    if command -v unzip > /dev/null; then unzip -q -o "$jar" "$@" -d "$dir"
    else mkdir -p "$dir" && (cd "$dir" && jar xf "$jar" "${@//\*/}"); fi
}

server_cp() {
    fetch "$SERVER_URL" "$DEPS/server-$MC-bundler.jar" "$SERVER_SHA1"
    local B="$DEPS/server-$MC/META-INF"
    if [ ! -f "$B/versions/$MC/server-$MC.jar" ]; then
        rm -rf "$DEPS/server-$MC"
        unpack "$DEPS/server-$MC-bundler.jar" "$DEPS/server-$MC" 'META-INF/versions/*' 'META-INF/libraries/*'
    fi
    SERVER_CP="$B/versions/$MC/server-$MC.jar"
    while IFS= read -r f; do SERVER_CP="$SERVER_CP:$f"; done < <(find "$B/libraries" -name '*.jar' | sort)
}

fabric_cp() {
    local M=https://maven.fabricmc.net/net/fabricmc
    fetch "$M/fabric-loader/$LOADER/fabric-loader-$LOADER.jar" "$DEPS/fabric-loader-$LOADER.jar" "$LOADER_SHA1"
    fetch "$M/sponge-mixin/$MIXIN/sponge-mixin-$MIXIN.jar" "$DEPS/sponge-mixin-$MIXIN.jar" "$MIXIN_SHA1"
    FABRIC_CP="$DEPS/fabric-loader-$LOADER.jar:$DEPS/sponge-mixin-$MIXIN.jar"
}

client_cp() {
    local M=https://maven.fabricmc.net/net/fabricmc
    fetch "$CLIENT_JSON_URL" "$DEPS/client-$MC.json" "$CLIENT_JSON_SHA1"
    fetch "$CLIENT_URL" "$DEPS/client-$MC.jar" "$CLIENT_SHA1"
    fetch "$M/fabric-api/fabric-api/$FABRIC_API/fabric-api-$FABRIC_API.jar" "$DEPS/fabric-api-$FABRIC_API.jar" "$FABRIC_API_SHA1"
    # the client's libraries (the plain jars; native-only artifacts are not needed to compile)
    while read -r url sha path; do
        fetch "$url" "$DEPS/libs/$path" "$sha"
    done < <(python3 - "$DEPS/client-$MC.json" <<'EOF'
import json, sys
for lib in json.load(open(sys.argv[1]))["libraries"]:
    a = lib.get("downloads", {}).get("artifact")
    if a and len(lib["name"].split(":")) == 3:
        print(a["url"], a["sha1"], a["path"])
EOF
)
    # the Fabric API modules the client mod uses, from inside the Fabric API jar
    local F="$DEPS/fapi-$FABRIC_API"
    [ -d "$F" ] || unpack "$DEPS/fabric-api-$FABRIC_API.jar" "$F" 'META-INF/jars/*'
    CLIENT_CP="$DEPS/client-$MC.jar"
    while IFS= read -r f; do CLIENT_CP="$CLIENT_CP:$f"; done < <(python3 - "$DEPS/client-$MC.json" "$DEPS/libs" <<'EOF'
import json, sys, os
for lib in json.load(open(sys.argv[1]))["libraries"]:
    a = lib.get("downloads", {}).get("artifact")
    if a and len(lib["name"].split(":")) == 3:
        print(os.path.join(sys.argv[2], a["path"]))
EOF
)
    for m in fabric-command-api-v2 fabric-key-mapping-api-v1 fabric-api-base; do
        CLIENT_CP="$CLIENT_CP:$(ls "$F"/META-INF/jars/$m-*.jar | head -1)"
    done
}

fastutil_cp() {   # the one library the planner (core/) uses, from Maven Central (the game bundles the same version)
    fetch "https://repo1.maven.org/maven2/it/unimi/dsi/fastutil/$FASTUTIL/fastutil-$FASTUTIL.jar" "$DEPS/fastutil-$FASTUTIL.jar" "$FASTUTIL_SHA1"
    FASTUTIL_CP="$DEPS/fastutil-$FASTUTIL.jar"
}

junit_cp() {
    fetch "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT/junit-platform-console-standalone-$JUNIT.jar" \
          "$DEPS/junit-platform-console-standalone-$JUNIT.jar" "$JUNIT_SHA1"
    JUNIT_CP="$DEPS/junit-platform-console-standalone-$JUNIT.jar"
}

# The source overlays of Minecraft $MC that exist: adapter/versions/$MC/src, then adapter/versions/$MC/<side>/src for
# each side named (server: the server-side body, used by the singleplayer mod and the headless runs; client: the
# client mod).
version_src() {  # side...
    local d="adapter/versions/$MC"
    [ -d "$ROOT/$d/src" ] && echo "$d/src"
    local s
    for s in "$@"; do [ -d "$ROOT/$d/$s/src" ] && echo "$d/$s/src"; done
    return 0
}

# javac over a list of source roots; extra args go to javac
compile() {  # out classpath roots...
    local out=$1 cp=$2; shift 2
    mkdir -p "$out"
    local list; list=$(mktemp)
    find "$@" -name '*.java' | sort > "$list"
    javac -nowarn -Xlint:-options -proc:none -encoding UTF-8 --release 25 -cp "$cp" -d "$out" @"$list"
    local rc=$?
    rm -f "$list"
    return $rc
}
