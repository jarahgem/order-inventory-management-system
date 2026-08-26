#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
"$APP_HOME/gradle/wrapper/bootstrap-wrapper.sh"

if [ -n "${JAVA_HOME:-}" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        printf '%s\n' "JAVA_HOME does not point to a valid Java installation: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD=$(command -v java || true)
    if [ -z "$JAVACMD" ]; then
        printf '%s\n' "Java was not found. Install Java 21 or set JAVA_HOME." >&2
        exit 1
    fi
fi

exec "$JAVACMD" \
    -Dorg.gradle.appname=gradlew \
    -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    "$@"
