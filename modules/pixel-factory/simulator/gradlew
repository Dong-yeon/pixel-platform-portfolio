#!/bin/sh

APP_HOME=$(cd "${0%/*}" || exit 1; pwd -P)
APP_BASE_NAME=${0##*/}
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

if [ ! -x "$JAVACMD" ] && ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no java command could be found in your PATH." >&2
    exit 1
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# shellcheck disable=SC2086
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
