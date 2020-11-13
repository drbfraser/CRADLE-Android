# <img src="readme-img/logo.png" width=40> CRADLE VSA System: Android Application

[![License](https://img.shields.io/github/license/Cradle-VSA/cradlemobile)](https://github.com/Cradle-VSA/cradlemobile/blob/master/LICENCE)

Android application for the CRADLE VSA System, a technological health care system to improve
maternal care and reduce preventable maternal deaths in Ugandan villages.

The Cradle VSA (Vital Signs Alert) is a portable medical device that can read a patient’s blood
pressure and heart rate, mainly used to detect abnormalities during pregnancy. The Cradle VSA
application can record the readings taken by the Cradle VSA device and upload it to the server. Our
application is designed for remote areas with an unstable internet connection; thus, so we
implemented an SMS feature.

# Setup

Install [Android Studio](https://developer.android.com/studio/) and import the project.

## Pre-push Git hook

The pre-push hook can be found in `hooks/pre-push.sh`. It ensures that the
static code analysis and the unit tests pass before pushing to the remote repo. If one of those
verification tasks fail, your commits won't be pushed to the repo and Android Studio or the command
line will notify you about it. The issues uncoovered would've been caught by the CI pipelines
anyway. (There are options in the push modal in Android Studio to not run Git hooks, but this isn't
recommended.)

Follow the below instructions for your development environment to setup the Git pre-push hook. After
setting it up, you can verify that it is setup properly by trying to push a commit via the command
line / terminal: `git push`. It should display the static code analysis and unit tests in the window
before pushing. Android Studio has the ability to also run these hooks using the Git push interface
in Android Studio.

### Mac and Linux Run the `setup-hooks.sh` script, which should set up the symbolic links targeted
in `.git/hooks` automatically:

    hooks/setup-hooks.sh

### Windows
Run the following command as an Admin

    mklink .git\hooks\pre-push ..\..\hooks\pre-push.sh

Or, for PowerShell, run the following command as an Admin

    New-Item -ItemType SymbolicLink -Path .\.git\hooks -Name pre-push -Value .\hooks\pre-push.sh

# Tests

## Unit tests

The app has some unit tests that can be run directly on a host computer via Android Studio
or the command `./gradlew test`.

These unit tests are run by the CI pipelines to verify merge requests and commits on master. They're
useful in ensuring there haven't been any regressions. Make sure the hooks from above are setup to
make it so that the unit tests are run automatically before you push to the remote repo.

## Instrumented tests

The app has some instrumented tests meant to be run on a physical or emulated device. These can also
be run directly in Android Studio, or via the command `./gradlew connectedAndroidTest`.

These tests are not run automatically, and these tests are not run by the CI pipelines.
Nevertheless, they're important, as they test various things such as database migrations and UI
flows automatically. These should be run often (at least once before a new release).

The UI tests need special setup on the device to be run. Follow the _Set up your test environment_
instructions outlined in
https://developer.android.com/training/testing/espresso/setup#set-up-environment:

> To avoid flakiness, we highly recommend that you turn off system animations on the virtual or
> physical devices used for testing. On your device, under Settings > Developer options, disable the
> following 3 settings
> * Window animation scale
> * Transition animation scale
> * Animator duration scale

<!-- The wiki isn't preferred. For an open source project, the wiki won't be available
     for others since it requires a computing ID to access. -->
> [Check Wiki for technical details](https://csil-git1.cs.surrey.sfu.ca/415-cradle/cradlemobile/-/wikis/home)
