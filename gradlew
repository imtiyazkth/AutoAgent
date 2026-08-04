#!/bin/sh
set -e
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_VERSION="8.9"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_DIR="$DIST_DIR/gradle-${GRADLE_VERSION}"
GRADLE_EXEC="$GRADLE_DIR/bin/gradle"

if [ ! -f "$GRADLE_EXEC" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$DIST_DIR"
    ZIP="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fL -o "$ZIP" "$URL"
    else
        wget -q -O "$ZIP" "$URL"
    fi
    echo "Extracting..."
    unzip -q "$ZIP" -d "$DIST_DIR"
    rm -f "$ZIP"
    chmod +x "$GRADLE_EXEC"
fi

exec "$GRADLE_EXEC" "$@"
