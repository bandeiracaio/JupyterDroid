#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

# Attempt to set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ] ; then
    JAVA_HOME=$(java -XshowSettings:all -version 2>&1 | grep 'java.home' | sed 's/.*java.home = //')
fi

exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
