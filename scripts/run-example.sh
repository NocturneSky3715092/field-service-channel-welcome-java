#!/bin/sh
set -eu
BUILD_DIR="${TMPDIR:-/tmp}/field-service-welcome-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/learnfield/onboarding/*.java
java -cp "$BUILD_DIR" learnfield.onboarding.FieldServiceOnboarding
