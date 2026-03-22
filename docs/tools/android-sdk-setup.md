# Android SDK Setup

This repository expects the Android SDK at:

`/Volumes/Files/userdata/Library/Android/sdk`

## Project-local setup

`local.properties` is configured with:

```properties
sdk.dir=/Volumes/Files/userdata/Library/Android/sdk
```

That is the main setting Gradle and Android Studio need for this repo.

## Recommended shell setup

Keep these in `~/.zprofile` so non-interactive shells are more likely to inherit them:

```sh
export ANDROID_HOME=/Volumes/Files/userdata/Library/Android/sdk
export ANDROID_SDK_ROOT=/Volumes/Files/userdata/Library/Android/sdk
export ANDROID_AVD_HOME=/Volumes/Files/userdata/.android/avd
export GRADLE_USER_HOME=/Volumes/Files/userdata/.gradle
export PATH="/Volumes/Files/userdata/Library/Android/sdk/platform-tools:$PATH"
```

Prefer the `PATH` entry over an `adb` alias.

## Notes

- `local.properties` is intentionally untracked by Git.
- Agent runs may still set explicit `env ...` prefixes when sandbox behavior makes that safer.
