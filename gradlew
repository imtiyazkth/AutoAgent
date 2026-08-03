#!/bin/sh
# Gradle Wrapper Script

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_VERSION="8.4"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_BIN="$DIST_DIR/gradle-${GRADLE_VERSION}/bin/gradle"

# Download if not cached
if [ ! -f "$GRADLE_BIN" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$DIST_DIR"
    ZIP="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
    
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$ZIP" "$DIST_URL" --silent --show-error
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$ZIP" "$DIST_URL"
    else
        echo "ERROR: curl or wget required"
        exit 1
    fi
    
    echo "Extracting Gradle $GRADLE_VERSION..."
    unzip -q "$ZIP" -d "$DIST_DIR"
    rm -f "$ZIP"
    chmod +x "$GRADLE_BIN"
fi

exec "$GRADLE_BIN" "$@"
