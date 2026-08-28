# RoutineApp

RoutineApp is a widget-first Android activity tracker. Add a task widget, tap it whenever the
activity is completed, and the widget immediately shows how long ago the last completion occurred.
After enough consistent completions, the app proposes an optional reminder cadence.

The Notes feature remains available as a secondary workflow.

## Build from a clean clone

Requirements:

- JDK 17
- Android SDK 34

From the repository root:

```bash
cd RoutineApp
./gradlew clean testDebugUnitTest assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.
