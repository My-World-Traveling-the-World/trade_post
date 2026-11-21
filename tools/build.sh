#!/usr/bin/env bash
set -euo pipefail
# 离线构建：不联网、不用 Gradle/Loom。
# 直接对着 intermediary 游戏 jar 编译（类名与运行时一致，无需 remap / refmap）。

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK=/home/guoke/tools/jdk-25.0.5+8
JAVA="$JDK/bin/java"; JAVAC="$JDK/bin/javac"; JAR="$JDK/bin/jar"
MC=/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/.fabric/remappedJars/minecraft-1.21.1-0.19.3/client-intermediary.jar
LIBS=/home/guoke/mc-linux/libraries
LOADER="$LIBS/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar"
MIXIN="$LIBS/net/fabricmc/sponge-mixin/0.17.3+mixin.0.8.7/sponge-mixin-0.17.3+mixin.0.8.7.jar"
BRIG="$LIBS/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar"
GSON="$LIBS/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
SLF4J=$(ls "$LIBS"/org/slf4j/slf4j-api/*/slf4j-api-*.jar | head -1)
DFU="$LIBS/com/mojang/datafixerupper/8.0.16/datafixerupper-8.0.16.jar"

for f in "$MC" "$LOADER" "$MIXIN" "$BRIG" "$GSON" "$SLF4J" "$DFU"; do
  [ -f "$f" ] || { echo "缺少依赖: $f" >&2; exit 1; }
done

OUT="$ROOT/build/classes"
DIST="$ROOT/build/libs"
rm -rf "$OUT"; mkdir -p "$OUT" "$DIST"

NETTY="$(ls /home/guoke/mc-linux/libraries/io/netty/netty-buffer/4.1.97.Final/*.jar 2>/dev/null | head -1):$(ls /home/guoke/mc-linux/libraries/io/netty/netty-common/4.1.97.Final/*.jar 2>/dev/null | head -1)"
CP="$MC:$LOADER:$MIXIN:$BRIG:$GSON:$SLF4J:$DFU:$NETTY"
CP="$CP:$(ls "$ROOT"/libs/*.jar | tr '\n' ':')"

find "$ROOT/src/main/java" -name '*.java' > /tmp/tp-sources.txt
"$JAVAC" -encoding UTF-8 --release 21 -nowarn -Xlint:-options \
  -cp "$CP" -d "$OUT" @/tmp/tp-sources.txt

cp -a "$ROOT/src/main/resources/." "$OUT/"

VERSION=$(grep -E '^mod_version' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d ' ')
VERSION=${VERSION:-0.1.0}
JARNAME="tradepost-$VERSION.jar"

sed "s/\${version}/$VERSION/" "$OUT/fabric.mod.json" > "$OUT/fabric.mod.json.tmp"
mv "$OUT/fabric.mod.json.tmp" "$OUT/fabric.mod.json"

"$JAR" --create --file "$DIST/$JARNAME" -C "$OUT" .
echo "OK: $DIST/$JARNAME"