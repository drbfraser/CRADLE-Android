# Cradle: Android Application

## Setup Instructions

### Android

Install [Android Studio](https://developer.android.com/studio/) and import the project.

#### Creating a Production Release

Create a new file in the root directory to hold the secure keystore details:
```shell
cp keystore.properties.template keystore.properties
```

This file will be ignored by Git.

Set your keystore information in this file.