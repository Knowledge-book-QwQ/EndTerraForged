#!/bin/bash
export JAVA_HOME="/root/.local/share/mise/installs/java/21.0.2"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=18080 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=18080"
GRADLE_BIN="/root/.local/share/mise/installs/gradle/8.14.4/gradle-8.14.4/bin/gradle"
cd /workspace && exec "$GRADLE_BIN" "$@"
