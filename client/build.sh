#!/bin/bash
# Builds the client mod: the bot (src/mcb) and the client side (src/mcbc), compiled against the vanilla 26.2 client
# (unobfuscated: no remapping), its libraries, Fabric Loader, Mixin and the two Fabric API modules it uses.
#   usage: ./build.sh [version]   -> dist/mcb-client-<version>.jar
set -e
cd "$(dirname "$0")"
V=${1:-0.1.0}
JAVAC=${JAVAC:-javac}
CP=vendor/mc/client-26.2.jar:vendor/fabric/fabric-loader-0.19.5.jar:vendor/fabric/sponge-mixin-0.17.4+mixin.0.8.7.jar
CP=$CP:$(find vendor/libs -name '*.jar' | sort | tr '\n' ':')
CP=$CP:$(ls vendor/fapi/META-INF/jars/fabric-command-api-v2-*.jar vendor/fapi/META-INF/jars/fabric-key-mapping-api-v1-*.jar vendor/fapi/META-INF/jars/fabric-api-base-*.jar | tr '\n' ':')
OUT=build/classes
rm -rf $OUT; mkdir -p $OUT
find src -name '*.java' > build/sources.txt
$JAVAC -nowarn -Xlint:-options -proc:none -encoding UTF-8 --release 25 -cp "$CP" -d $OUT @build/sources.txt
sed "s/\${version}/$V/" resources/fabric.mod.json > $OUT/fabric.mod.json
cp resources/mcb-client.mixins.json $OUT/
cp -r resources/assets $OUT/
mkdir -p dist
rm -f dist/mcb-client-$V.jar
(cd $OUT && jar --create --file ../../dist/mcb-client-$V.jar .)
echo "built dist/mcb-client-$V.jar ($(find $OUT -name '*.class' | wc -l) classes)"
