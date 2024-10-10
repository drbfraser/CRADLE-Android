# <img src="readme-img/logo.png" width=40> CRADLE VSA System: Android Application

[![License](https://img.shields.io/github/license/Cradle-VSA/cradlemobile)](https://github.com/Cradle-VSA/cradlemobile/blob/master/LICENCE)

Android application for the CRADLE VSA System, a technological health care system to improve
maternal care and reduce preventable maternal deaths in Ugandan villages.

The Cradle VSA (Vital Signs Alert) is a portable medical device that can read a patient’s blood
pressure and heart rate, mainly used to detect abnormalities during pregnancy. The Cradle VSA
application can record the readings taken by the Cradle VSA device and upload it to the server. Our
application is designed for remote areas with an unstable internet connection, featuring 
synchronization over either internet or SMS (text message).

## Setup

1. Set up and run the [Cradle Platform](https://github.sfu.ca/bfraser/415-Cradle-Platform/blob/main/docs/development.md) project. The Docker image must be running to provide a back-end for this app.
    * Be sure to seed at least `seed_test_data`
1. Download and install [Android Studio](https://developer.android.com/studio/).
1. Clone [this repo](https://github.sfu.ca/cradle-project/Cradle-Mobile) to your computer.
1. Open the Git repository from Android Studio.
1. Edit gradle version in Android Studio
    * File -> Project Structure -> Project
        * Android Gradle Plugin Version: `8.2.1`
        * Gradle Version: `8.2`
1. Edit JDK version in Android Studio
    * File -> Settings -> Build, Execution, Deployment -> Build Tools -> Gradle
        * Gradle JDK: `JetBrains Runtime version 17`
        * If it's not one of the options, click on "Download JDK" and select the appropriate version
1. Run the app in the emulator. The first build may take several minutes.
1. Connect the app to the (running) Cradle Platform from the login settings (top right):
    * Hostname: 10.0.2.2
    * Port: 5000
    * Use HTTPS: OFF
    > INFO:  
    > IP 10.0.2.2 is a special IP address in Android Studio to access the local machine that the emulator isrunning on.  
    > Port 5000 is the port that the Flask container deploys to.
1. Log in with a [default username and password](https://github.sfu.ca/cradle-project/Cradle-Platform#default-usernames--passwords).  Admin provides the most access.

## Running the app on a physical Android phone

1. Enable Developer Settings on your phone.
1. Enable USB debugging and connect your phone among the "Running devices" in Android Studio.
1. Build the app to your phone. 
1. Open a terminal and run `ipconfig` to find your computer's IPv4 IP address (something like 192.168.x.x).
1. Connect the app to the (running) Cradle Platform from the login settings (top right):
    * Hostname: <<your_computer_IP>>
    * Port: 5000
    * Use HTTPS: OFF

### Useful Documentation
* Cradle Mobile Onboarding
    https://docs.google.com/document/d/1okJHo1OMfRZbkep-37rOIpApkXmZgxp1Uwmf_Evaguo/edit
* Mobile Testing with Local CradlePlatform
    https://docs.google.com/document/d/1ohbJqzYMEzDeSj_EVndMZvnp9ShB1A1q682QIcr-hNE/edit

### Troubleshooting

* Building may be disrupted in there is an external JDK installed.  Uninstall that JDK, delete the repo, and begin again at step 1. above.
* Installing may be flaky with emulators lacking a Play Store.  Try an emulator with a Play Store.

## Architecture

The app uses Kotlin, with some remaining Java.

We use various components from [Android Jetpack](https://developer.android.com/jetpack) and other
libraries:

* [Room](https://developer.android.com/topic/libraries/architecture/room) is used for managing the
SQLite3 database.
* [ViewModels](https://developer.android.com/topic/libraries/architecture/viewmodel) are used to
handle the bulk of the logic code in some areas of the app so that the Activities / Fragments focus
on UI-related code.
* The [Data Binding Library](https://developer.android.com/topic/libraries/data-binding) is used in
areas to reduce the amount of boilerplate code.
* [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) is used to
run syncing in the background.
* The
[Paging 3 library](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) is
used with Room for pagination of `RecyclerView`s.
* The [Navigation component](https://developer.android.com/guide/navigation) is used in some places
such as the new patient / reading creation Activity to navigate between fragments.
* [Espresso](https://developer.android.com/training/testing/espresso/) is used for UI tests. Also we
use [mockk](https://mockk.io/) for mocking in unit tests.

## Pre-push Git hook

When the Git hook is set up, static code analysis and unit tests will be run upon a `git push` 
command.  Tests must pass before git will allow your code changes to be pushed to the remote repo.
The GitHub CI pipeline will run the tests again before merging a PR.
(There are options in the push modal in Android Studio to not run Git hooks, but this isn't
recommended.)

The pre-push hook can be found in `hooks/pre-push.sh`. 

### Mac and Linux

Run the `setup-hooks.sh` script, which should set up the symbolic links targeted
in `.git/hooks` automatically:

    hooks/setup-hooks.sh

### Windows

Run the following command as an Admin

    mklink .git\hooks\pre-push ..\..\hooks\pre-push.sh

Or, for PowerShell, run the following command as an Admin

    New-Item -ItemType SymbolicLink -Path .\.git\hooks -Name pre-push -Value .\hooks\pre-push.sh

### Verify the setup

After setting up the Git pre-push hook, try to push a commit via the command line / terminal:
`git push`. You shouuld see the static code analysis and unit tests displayed in the window
before pushing. Android Studio's Git push interface will also run these hooks, but it is not as 
verbose unless there's a failure.

## Tests

### Unit tests

The app has some unit tests that can be run directly on a host computer via Android Studio
or the command `./gradlew test`.

`./gradlew detekt` runs static code analysis and style correction ([read more](https://detekt.dev)). 
It is likely to fail the first pass. If there are issues after a second run, then manual edits will 
be required. 

NOTE: Google doc suggested that these should be manually run before each push, but that may be part 
of the automatic pre-push hooks. Delete or incorporate this note once clarified. There is also 
reference to running detekt in a more verbose mode. Does that refer to running in the command line
(as opposed to via Android Studio push), or to a specific flag?  I did not find a flag that increases
verbosity in the [detekt help page](https://detekt.dev/docs/gettingstarted/cli#use-the-cli).

These unit tests are run by the GitHub CI pipelines to verify merge requests and commits on master. They're
useful in ensuring there haven't been any regressions. Make sure the hooks from above are setup to
make it so that the unit tests are run automatically before you push to the remote repo.

### Instrumented tests

The app has some instrumented tests meant to be run on a physical or emulated device. These can also
be run directly in Android Studio, or via the command `./gradlew connectedAndroidTest`.

These tests are not run automatically, and these tests are not run by the CI pipelines.
Nevertheless, they're important, as they test various things such as database migrations and UI
flows automatically. These should be run often (at least once before a new release).

### UI tests

The UI tests need special setup on the device to be run.

1. Follow the _Set up your test environment_ instructions outlined in
   https://developer.android.com/training/testing/espresso/setup#set-up-environment:

   > To avoid flakiness, we highly recommend that you turn off system animations on the virtual or
   > physical devices used for testing. On your device, under Settings > Developer options, disable
   > the following 3 settings
   > * Window animation scale
   > * Transition animation scale
   > * Animator duration scale

2. Uninstall or log out of the app if it's already installed. The UI tests LoginActivity, and those
   tests can fail if already logged in.

## Pinning TLS certificate public keys
We're currently pinning TLS public keys for the server. There are two ways to setup pinning: by
following
[OkHttp's guide](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/#setting-up-certificate-pinning)
or by using the bash script located in
[scripts/x509-subject-pubkey-hash.sh](scripts/x509-subject-pubkey-hash.sh). If you're paranoid, try
both ways and see if the pins match up.

## Other links

<!-- The wiki isn't preferred. For an open source project, the wiki won't be available
     for others, since it requires a computing ID to access. -->
[Check Wiki for technical details](https://github.sfu.ca/cradle-project/Cradle-Mobile/wiki)
