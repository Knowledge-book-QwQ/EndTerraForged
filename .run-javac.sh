#!/bin/bash
# Fast javac compile-check for single files (avoids gradle's 6-min cold start).
# Usage: bash .run-javac.sh <file1.java> [file2.java ...]
# Exits 0 on success, non-zero on compile error.
DFU8="/root/.gradle/caches/modules-2/files-2.1/com.mojang/datafixerupper/8.0.16/67d4de6d7f95d89bcf5862995fb854ebaec02a34/datafixerupper-8.0.16.jar"
MC_JAR="/root/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.1-loom.mappings.1_21_1.layered+hash.40359-v2/minecraft-merged-1.21.1-loom.mappings.1_21_1.layered+hash.40359-v2.jar"
EXTRA=$(find /root/.gradle/caches -name "guava-33.0.0-jre.jar" -o -name "brigadier-1.3.10.jar" -o -name "authlib-6.0.54.jar" -o -name "log4j-api-2.22.1.jar" -o -name "gson-2.10.1.jar" -o -name "slf4j-api-2.0.16.jar" 2>/dev/null | tr '\n' ':')
CP="${DFU8}:${MC_JAR}:${EXTRA}"
OUT="/tmp/javac-out"
rm -rf "$OUT" && mkdir -p "$OUT"
JAVA_HOME="/root/.local/share/mise/installs/java/21.0.2"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
javac -d "$OUT" -cp "$CP" --release 21 -Xlint:none "$@"
