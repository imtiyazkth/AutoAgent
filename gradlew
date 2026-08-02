#!/bin/sh

# AutoAgent gradlew wrapper
# Works in both Termux (uses system gradle) and GitHub Actions (uses gradle wrapper)

# Try wrapper first (GitHub Actions)
GRADLE_WRAPPER="$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$GRADLE_WRAPPER" ]; then
    exec java -jar "$GRADLE_WRAPPER" "$@"
fi

# Fallback to system gradle (Termux)
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

echo "ERROR: Neither gradle wrapper nor system gradle found"
exit 1
