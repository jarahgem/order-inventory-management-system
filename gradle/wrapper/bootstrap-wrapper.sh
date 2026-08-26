#!/bin/sh
set -eu

WRAPPER_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
WRAPPER_URL=${GRADLE_WRAPPER_JAR_URL:-https://raw.githubusercontent.com/gradle/gradle/v8.14.5/gradle/wrapper/gradle-wrapper.jar}
WRAPPER_SHA256=7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172

checksum() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        printf '%s\n' "Cannot verify the Gradle Wrapper JAR: install sha256sum or shasum." >&2
        return 1
    fi
}

is_valid_wrapper() {
    [ -f "$WRAPPER_JAR" ] && [ "$(checksum "$WRAPPER_JAR")" = "$WRAPPER_SHA256" ]
}

if ! is_valid_wrapper; then
    mkdir -p "$WRAPPER_DIR"
    temporary_jar="$WRAPPER_JAR.tmp.$$"
    trap 'rm -f "$temporary_jar"' 0 1 2 15

    printf '%s\n' "Downloading the official Gradle 8.14.5 Wrapper JAR..." >&2
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --retry 3 --connect-timeout 20 \
            "$WRAPPER_URL" --output "$temporary_jar"
    elif command -v wget >/dev/null 2>&1; then
        wget --tries=3 --timeout=20 --output-document="$temporary_jar" "$WRAPPER_URL"
    else
        printf '%s\n' "Install curl or wget to bootstrap the Gradle Wrapper." >&2
        exit 1
    fi

    actual_checksum=$(checksum "$temporary_jar")
    if [ "$actual_checksum" != "$WRAPPER_SHA256" ]; then
        printf '%s\n' "Gradle Wrapper JAR checksum verification failed." >&2
        printf '%s\n' "Expected: $WRAPPER_SHA256" >&2
        printf '%s\n' "Actual:   $actual_checksum" >&2
        exit 1
    fi

    mv "$temporary_jar" "$WRAPPER_JAR"
    trap - 0 1 2 15
fi
