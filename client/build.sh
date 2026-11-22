#!/usr/bin/env bash
# Builds build/mcb-client-<version>.jar.
#
# Minecraft 26.2 ships unobfuscated, so the mod compiles straight against the vanilla client classes: no Gradle,
# no remapping. On the first run this script downloads, into .deps/ (never committed), each checked against a
# pinned SHA-1:
#   - the 26.2 version manifest, the vanilla client jar and its libraries, from Mojang
#   - Fabric Loader 0.19.5, Sponge Mixin and Fabric API 0.159.0+26.2, from maven.fabricmc.net
#
# Needs: bash, curl, unzip, sha1sum, python3, and a JDK 25 (javac, jar).
# Usage: ./build.sh [version]          (default 0.2.0)
set -euo pipefail
cd "$(dirname "$0")"
V=${1:-0.2.0}
DEPS=.deps

fetch() {  # url file sha1
    if [ ! -f "$2" ] || ! echo "$3  $2" | sha1sum -c --status; then
        echo "downloading $(basename "$2")"
        mkdir -p "$(dirname "$2")"
        curl -fsSL -o "$2.part" "$1"
        echo "$3  $2.part" | sha1sum -c --status || { echo "SHA-1 mismatch for $1" >&2; exit 1; }
        mv "$2.part" "$2"
    fi
}

M=https://maven.fabricmc.net/net/fabricmc
mkdir -p "$DEPS/libs"
fetch https://piston-meta.mojang.com/v1/packages/f28ad96ed5d65e2438da836144a3de15bb9cafe7/26.2.json \
      "$DEPS/26.2.json" f28ad96ed5d65e2438da836144a3de15bb9cafe7
fetch https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar \
      "$DEPS/client-26.2.jar" 2dc72797acbc1b63fc16a11c4ac393605f453754
fetch $M/fabric-loader/0.19.5/fabric-loader-0.19.5.jar \
      "$DEPS/fabric-loader-0.19.5.jar" ff9e65cffca4a67f31523e1807fe0855940fcbfa
fetch "$M/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar" \
      "$DEPS/sponge-mixin-0.17.4+mixin.0.8.7.jar" 5f66cc9f59b8efaa942155a3d5a30599bf6640dd
fetch "$M/fabric-api/fabric-api/0.159.0+26.2/fabric-api-0.159.0+26.2.jar" \
      "$DEPS/fabric-api-0.159.0+26.2.jar" 5307b87f8336f27e8f963263ac514f4bc6175d90

# the client's libraries (the plain jars; native-only artifacts are not needed to compile)
while read -r url sha path; do
    fetch "$url" "$DEPS/libs/$path" "$sha"
done < <(python3 - "$DEPS/26.2.json" <<'EOF'
import json, sys
for lib in json.load(open(sys.argv[1]))["libraries"]:
    a = lib.get("downloads", {}).get("artifact")
    if a and len(lib["name"].split(":")) == 3:
        print(a["url"], a["sha1"], a["path"])
EOF
)

# the Fabric API modules the mod uses, from inside the Fabric API jar
if [ ! -d "$DEPS/fapi" ]; then
    unzip -q "$DEPS/fabric-api-0.159.0+26.2.jar" 'META-INF/jars/*' -d "$DEPS/fapi"
fi

CP="$DEPS/client-26.2.jar:$DEPS/fabric-loader-0.19.5.jar:$DEPS/sponge-mixin-0.17.4+mixin.0.8.7.jar"
while IFS= read -r f; do CP="$CP:$f"; done < <(find "$DEPS/libs" -name '*.jar' | sort; \
    ls "$DEPS"/fapi/META-INF/jars/fabric-command-api-v2-*.jar "$DEPS"/fapi/META-INF/jars/fabric-key-mapping-api-v1-*.jar \
       "$DEPS"/fapi/META-INF/jars/fabric-api-base-*.jar)

OUT=build/classes
rm -rf build; mkdir -p "$OUT"
find src -name '*.java' > build/sources.txt
javac -nowarn -Xlint:-options -proc:none -encoding UTF-8 --release 25 -cp "$CP" -d "$OUT" @build/sources.txt
sed "s/\${version}/$V/" resources/fabric.mod.json > "$OUT/fabric.mod.json"
cp resources/mcb-client.mixins.json "$OUT/"
cp -r resources/assets "$OUT/"
cp ../LICENSE "$OUT/" 2>/dev/null || true
(cd "$OUT" && jar --create --file "../mcb-client-$V.jar" .)
echo "built build/mcb-client-$V.jar ($(find "$OUT" -name '*.class' | wc -l) classes)"
