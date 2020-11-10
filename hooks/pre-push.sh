#!/bin/sh

echo "hooks/pre-push.sh: Running static analysis (./gradlew detekt)..."

# Validate Kotlin code with detekt
./gradlew app:detekt --no-daemon
STATUS=$?
if [ $STATUS -ne 0 ] ; then
    cat <<EOF >&2
**********************************************************
hooks/pre-push.sh: Detekt failed
Static code analysis found issues

Use the command ./gradlew detekt or the detekt Gradle task
in Android Studio to run the static code analysis for details.
Please fix and commit the above issues before pushing
**********************************************************
EOF
    exit 1
fi

# Run unit tests before pushing
echo "hooks/pre-push.sh: Running unit tests (./gradlew test)..."
./gradlew test
STATUS=$?
if [ $STATUS -ne 0 ] ; then
    cat <<EOF >&2
**********************************************************
hooks/pre-push.sh: Unit tests failed

Use the command ./gradlew test or run the unit tests
in Android Studio for details.
Please fix and commit the above issues before pushing
**********************************************************
EOF
    exit 1
fi

echo "hooks/pre-push.sh: Tests passed. Pushing to repo"
exit 0
