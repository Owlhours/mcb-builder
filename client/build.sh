#!/usr/bin/env bash
# Builds client/build/mcb-client-<version>.jar, the client mod.
#
# Minecraft ships unobfuscated, so the mod compiles straight against the vanilla client classes: no Gradle, no
# remapping. On the first run tools/deps.sh downloads, into .deps/ (never committed), each checked against the
# SHA-1 pinned in adapter/versions/<mc>.env:
#   - the version manifest, the vanilla client jar and its libraries, from Mojang
#   - Fabric Loader, Sponge Mixin and Fabric API, from maven.fabricmc.net
#
# Needs: bash, curl, unzip, sha1sum, python3, and a JDK 25 (javac, jar).
# Usage: client/build.sh [version]          (default 0.2.0; MC=<version> picks the Minecraft version, default 26.2)
set -euo pipefail
cd "$(dirname "$0")/.."
V=${1:-0.2.0}
. tools/deps.sh
client_cp
fabric_cp
OUT=client/build/classes
rm -rf client/build; mkdir -p "$OUT"
# core (the planner), adapter/src (the Minecraft code both mods share), the client mod itself, and the version's
# source overlays (adapter/versions/<mc>/)
compile "$OUT" "$CLIENT_CP:$FABRIC_CP" core/src adapter/src adapter/client/src $(version_src client)
sed "s/\${version}/$V/; s/\${minecraft}/$MC/" adapter/client/resources/fabric.mod.json > "$OUT/fabric.mod.json"
cp adapter/client/resources/mcb-client.mixins.json "$OUT/"
cp -r adapter/client/resources/assets "$OUT/"
cp LICENSE "$OUT/" 2>/dev/null || true
(cd "$OUT" && jar --create --file "../mcb-client-$V.jar" .)
echo "built client/build/mcb-client-$V.jar for Minecraft $MC ($(find "$OUT" -name '*.class' | wc -l) classes)"
