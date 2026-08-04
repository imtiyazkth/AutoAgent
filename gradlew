#!/bin/sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_VERSION="8.9"
GRADLE_BIN="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin/*/gradle-${GRADLE_VERSION}/bin/gradle"

# Try local wrapper first
GRADLE_EXEC=$(ls $GRADLE_BIN 2>/dev/null | head -1)

if [ -z "$GRADLE_EXEC" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
    mkdir -p "$DIST_DIR"
    ZIP="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -L --fail -o "$ZIP" "$URL"
    else
        wget -q -O "$ZIP" "$URL"
    fi
    unzip -q "$ZIP" -d "$DIST_DIR"
    rm -f "$ZIP"
    GRADLE_EXEC=$(ls $GRADLE_BIN 2>/dev/null | head -1)
fi

if [ -z "$GRADLE_EXEC" ]; then
    echo "ERROR: Could not find gradle" >&2
    exit 1
fi

chmod +x "$GRADLE_EXEC"
exec "$GRADLE_EXEC" "$@"
