#!/bin/sh
set -eu

MAVEN_VERSION="3.9.11"
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST_ROOT="$BASE_DIR/.mvn/dist"
MAVEN_HOME="$DIST_ROOT/apache-maven-$MAVEN_VERSION"
ARCHIVE="$DIST_ROOT/apache-maven-$MAVEN_VERSION-bin.tar.gz"
BASE_URL="${MAVEN_DISTRIBUTION_BASE_URL:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven}"
DOWNLOAD_URL="$BASE_URL/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
    mkdir -p "$DIST_ROOT"
    if [ ! -f "$ARCHIVE" ]; then
        printf '%s\n' "Downloading Apache Maven $MAVEN_VERSION..." >&2
        if command -v curl >/dev/null 2>&1; then
            curl --fail --location --silent --show-error "$DOWNLOAD_URL" --output "$ARCHIVE"
            curl --fail --location --silent --show-error "$DOWNLOAD_URL.sha512" --output "$ARCHIVE.sha512"
        elif command -v wget >/dev/null 2>&1; then
            wget --quiet "$DOWNLOAD_URL" --output-document="$ARCHIVE"
            wget --quiet "$DOWNLOAD_URL.sha512" --output-document="$ARCHIVE.sha512"
        else
            printf '%s\n' "Neither curl nor wget is available. Install Maven $MAVEN_VERSION or one of those tools." >&2
            exit 1
        fi
    fi

    EXPECTED=$(tr -d '[:space:]' < "$ARCHIVE.sha512")
    if command -v sha512sum >/dev/null 2>&1; then
        ACTUAL=$(sha512sum "$ARCHIVE" | awk '{print $1}')
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL=$(shasum -a 512 "$ARCHIVE" | awk '{print $1}')
    elif command -v openssl >/dev/null 2>&1; then
        ACTUAL=$(openssl dgst -sha512 "$ARCHIVE" | awk '{print $NF}')
    else
        printf '%s\n' "A SHA-512 tool (sha512sum, shasum, or openssl) is required." >&2
        exit 1
    fi
    if [ "$EXPECTED" != "$ACTUAL" ]; then
        printf '%s\n' "Maven distribution checksum verification failed." >&2
        rm -f "$ARCHIVE"
        exit 1
    fi

    tar -xzf "$ARCHIVE" -C "$DIST_ROOT"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
