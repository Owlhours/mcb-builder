#!/usr/bin/env bash
# Builds build/mcb-builder-<version>.jar.
#
# Minecraft 26.2 ships unobfuscated, so the mod compiles straight against the vanilla server classes: no Gradle,
# no remapping. On the first run this script downloads, into .deps/ (never committed):
#   - the vanilla 26.2 server jar from Mojang (it is a bundler; its inner server jar and libraries are unpacked)
#   - Fabric Loader 0.19.5 and Sponge Mixin from maven.fabricmc.net
# and checks each against a pinned SHA-1.
#
# Needs: bash, curl, unzip, sha1sum, and a JDK 25 (javac, jar).
# Usage: ./build.sh [version]          (default 1.0.0)
set -euo pipefail
cd "$(dirname "$0")"
V=${1:-1.0.0}
DEPS=.deps

fetch() {  # url file sha1
    if [ ! -f "$2" ] || ! echo "$3  $2" | sha1sum -c --status; then
        echo "downloading $(basename "$2")"
        curl -fsSL -o "$2.part" "$1"
        echo "$3  $2.part" | sha1sum -c --status || { echo "SHA-1 mismatch for $1" >&2; exit 1; }
        mv "$2.part" "$2"
    fi
}

mkdir -p "$DEPS"
fetch https://piston-data.mojang.com/v1/objects/823e2250d24b3ddac457a60c92a6a941943fcd6a/server.jar \
      "$DEPS/server-26.2-bundler.jar" 823e2250d24b3ddac457a60c92a6a941943fcd6a
fetch https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar \
      "$DEPS/fabric-loader-0.19.5.jar" ff9e65cffca4a67f31523e1807fe0855940fcbfa
fetch "https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar" \
      "$DEPS/sponge-mixin-0.17.4+mixin.0.8.7.jar" 5f66cc9f59b8efaa942155a3d5a30599bf6640dd

B="$DEPS/bundle/META-INF"
if [ ! -f "$B/versions/26.2/server-26.2.jar" ]; then
    rm -rf "$DEPS/bundle"
    unzip -q "$DEPS/server-26.2-bundler.jar" 'META-INF/versions/*' 'META-INF/libraries/*' -d "$DEPS/bundle"
fi

CP="$B/versions/26.2/server-26.2.jar"
while IFS= read -r f; do CP="$CP:$f"; done < <(find "$B/libraries" -name '*.jar' | sort)
CP="$CP:$DEPS/fabric-loader-0.19.5.jar:$DEPS/sponge-mixin-0.17.4+mixin.0.8.7.jar"

OUT=build/classes
rm -rf build; mkdir -p "$OUT"
# the shared planner (core/), the Minecraft adapter (adapter/, without the headless runner) and the mod's own glue (src/)
find ../core/src ../adapter/src src -name '*.java' | grep -v '/mcb/headless/' | sort > build/sources.txt
javac -nowarn -Xlint:-options -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT" @build/sources.txt
sed "s/\${version}/$V/" resources/fabric.mod.json > "$OUT/fabric.mod.json"
cp resources/mcb-builder.mixins.json "$OUT/"
cp ../LICENSE "$OUT/" 2>/dev/null || true
(cd "$OUT" && jar --create --file "../mcb-builder-$V.jar" .)
echo "built build/mcb-builder-$V.jar ($(find "$OUT" -name '*.class' | wc -l) classes)"
