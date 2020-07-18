#!/bin/sh

echo "Running static analysis..."

# Validate Kotlin code with detekt
./gradlew app:detekt --no-daemon

status=$?

if [ "$status" = 0 ] ; then
    echo "Static analysis found no issues. Ready to push."
    exit 0
else
    echo 1>&2 "Static analysis found issues......"
	  echo "**********************************************************"
	  echo "                 Detekt failed                            "
		echo ""
		echo "   Use the command: ./gradlew detekt to run analysis"
		echo ""
		echo " Please fix and commit the above issues before pushing    "
	  echo "**********************************************************"
    exit 1
fi

