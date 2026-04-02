# Puzzle

## What Is This App About

Puzzle is an Android jigsaw puzzle app built for short, touch-first play sessions.

The current app lets the player:

- browse a built-in puzzle gallery
- receive catalog updates from a remote cloud-hosted puzzle catalog when newer content is available
- import their own images from the system photo picker
- choose a piece count before starting
- solve a jigsaw puzzle with drag-and-drop piece placement
- continue unfinished sessions from saved local progress
- customize the gameplay background

The app is portrait-only and designed for offline-first use. Built-in puzzle content is bundled with the app, the gallery can refresh from a cloud catalog when the device is online, and user-imported images are stored locally on device.

## What It Was Written With

- Kotlin
- Jetpack Compose for UI
- AndroidX Navigation Compose for screen navigation
- DataStore for saved progress and gameplay preferences
- Coroutines and Flow for async work and state streams
- A separate pure Kotlin jigsaw domain layer for session state, snapping, layout generation, and progress conversion

Project modules are split by responsibility:

- `app`: Android entry point and navigation wiring
- `core:model`: shared models
- `core:designsystem`: theme and reusable UI pieces
- `domain:jigsaw`: gameplay/session logic
- `data:catalog`: bundled catalog, uploaded image import, and catalog sync/storage
- `data:progress`: local progress persistence
- `feature:gallery`: puzzle gallery
- `feature:piececount`: piece-count selection
- `feature:gameplay`: active puzzle session UI

## Technical Details

### Android Configuration

- Compile SDK: 36
- Target SDK: 36
- Minimum SDK: 26
- Java target: 17
- Kotlin/JVM target: 17

### UI and Runtime Behavior

- Orientation: portrait only
- Main activity: `.MainActivity`
- Launcher activity: yes
- RTL support: enabled
- Backup flag: `android:allowBackup="true"`
- Release minification: disabled in current Gradle config

### Permissions

The current manifest declares no Android permissions.

Notes:

- image import uses the Android photo picker flow rather than broad storage permissions
- the app stores progress and imported images in app-local storage

### Storage and Data

- Puzzle progress is stored locally with DataStore
- Imported user images are stored under the app's private files directory
- Built-in puzzle images are bundled in `app/src/main/assets/puzzles`
- Gameplay background selection is stored locally
