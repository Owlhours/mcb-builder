#!/usr/bin/env bash
# Builds builder/build/mcb-builder-<version>.jar, the singleplayer mod.
#
# Minecraft ships unobfuscated, so the mod compiles straight against the vanilla server classes: no Gradle, no
# remapping. On the first run tools/deps.sh downloads, into .deps/ (never committed), the vanilla server jar from
# Mojang (a bundler: its inner server jar and libraries are unpacked) and Fabric Loader and Sponge Mixin from
# maven.fabricmc.net, and checks each against the SHA-1 pinned in adapter/versions/<mc>.env.
#
# The jar holds the shared planner (core/), the Minecraft adapter (adapter/src) and the mod's own glue
# (adapter/builder/src).
#
# Needs: bash, curl, unzip, sha1sum, and a JDK 25 (javac, jar).
# Usage: builder/build.sh [version]          (default 1.0.0; MC=<version> picks the Minecraft version, default 26.2)
set -euo pipefail
cd "$(dirname "$0")/.."
V=${1:-1.0.0}
. tools/deps.sh
server_cp
fabric_cp
OUT=builder/build/classes
rm -rf builder/build; mkdir -p "$OUT"
compile "$OUT" "$SERVER_CP:$FABRIC_CP" core/src adapter/src adapter/builder/src
sed "s/\${version}/$V/; s/\${minecraft}/$MC/" adapter/builder/resources/fabric.mod.json > "$OUT/fabric.mod.json"
cp adapter/builder/resources/mcb-builder.mixins.json "$OUT/"
cp LICENSE "$OUT/" 2>/dev/null || true
(cd "$OUT" && jar --create --file "../mcb-builder-$V.jar" .)
echo "built builder/build/mcb-builder-$V.jar for Minecraft $MC ($(find "$OUT" -name '*.class' | wc -l) classes)"
