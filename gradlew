#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

# Resolve APP_HOME to the directory containing this script
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")/"$link"
    fi
done
APP_HOME=$(cd "$(dirname "$PRG")" && pwd)

# Attempt to set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ] ; then
    JAVA_HOME=$(java -XshowSettings:all -version 2>&1 | grep 'java.home' | sed 's/.*java.home = //')
fi

exec "$JAVA_HOME/bin/java" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
