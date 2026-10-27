# Button Forge Studio

Phase 1, step 1: Gradle skeleton + `:geometry` module (pure JVM).

## First-time setup
The Gradle wrapper is not included (it could not be generated offline). Once, on a machine with Gradle installed:

    gradle wrapper --gradle-version 8.10.2

then commit `gradlew`, `gradlew.bat` and `gradle/wrapper/`. Update the unverified versions in `gradle/libs.versions.toml` first.

## Verify
    ./gradlew :geometry:test

## Phase 1, step 2
Adds `:document` (layers, shapes with separate geometry/appearance, button states, export settings, id counters).
Verify: `gradle :geometry:test :document:test --continue`

## Phase 1, step 3
Adds `:project-storage` (.bfs ZIP archive, DTO schema v1, migrations, atomic save, corrupt/future-version handling).
Verify: `gradle :geometry:test :document:test :project-storage:test --continue`
Not included yet: the schema snapshot test (needs one green run to generate the golden schema file).

## Phase 1, step 4
Adds `:editor-core` (undo/redo history, drag transactions, point editing, shape/points modes, touch controller)
plus `HitTester` and `ArcLengthDistribution.fillTo` in `:geometry`, and `Transform.toWorld/toLocal` in `:document`.
Verify: `gradle test --continue` (or the four module test tasks).

## Phase 1, step 5 (first APK)
Adds `:renderer`, `:editor-ui`, `:app`, geometry primitives, `Viewport`, `addShapeLayer`.
The debug APK is built by `.github/workflows/build-apk.yml` (artifact `ButtonForgeStudio-debug-apk`) or locally with
`gradle :app:assembleDebug`. minSdk is 26 (the storage module uses java.nio.file). Versions in `libs.versions.toml` are unverified.

## Phase 1, step 6
Adds `:integration-tests` (editor -> storage round trip, hand-written v1 fixture, opt-in perf baseline),
`docs/boolean-ops-spike.md`, `docs/performance.md`, and the manual workflow `perf-baseline.yml`.

## Phase 2, step 1
Adds operations 3 and 4 to `:geometry` (`Resampling`, `CurveRefit` with corner detection, `PathError`) and
`previewRecount` / `previewSimplify` / `applyReplacement` to `EditorSession`. JVM only; no UI changes yet.

## Phase 2, step 2
Handle dragging (the other handle follows Sharp / Smooth / Symmetric), corner-type buttons, and a preview-then-apply
panel for approximate re-pointing and simplifying. Build tag on screen: "7أ".

## Phase 2, step 3
Layer management: bring forward / send back, duplicate (groups too, with fresh ids), show/hide, lock, delete — each one
undo step. `Document.locateLayer` added. Build tag "7ب".

## Phase 2, step 4
Freehand drawing (smoothed, re-spaced and fitted into editable Béziers; shaky input does not create false corners)
and the pen tool (tap = corner, drag = smooth point with handle, tap the first point to close). Windowed corner
detection in `CurveRefit`. Build tag "8أ".
