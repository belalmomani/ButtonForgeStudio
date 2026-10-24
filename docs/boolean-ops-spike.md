# Boolean operations: spike notes (decision D5)

Status: research only. Nothing here has been run on a device. The decision is taken at the end of Phase 1.

## What was verified (Android documentation, searched 2026-10-24)
- `androidx.graphics.path.PathIterator` exists in the `androidx.graphics:graphics-path` library. It iterates the segments of an
  `android.graphics.Path`. Conic segments are converted to quadratics by default, with a tolerance, or can be kept as conics.
- The same iterator is used by Compose UI's own `Path`, which also exposes boolean operations (`Path.op`) according to the
  Compose change notes found in the search.
- Consequence for us: a boolean result read back from `android.graphics.Path` contains lines, quadratics and (approximated)
  conics. Each quadratic must be degree-elevated to a cubic (exact), and conics (circles, ovals) arrive as approximations.

## Still unverified
- Minimum API level of `Path.op` on the platform, and its robustness on tangent and coincident edges.
- Current version and API-level support of `graphics-path`.
- Licence and maintenance of any JVM boolean library (Clipper2 ports, JTS).
- Behaviour for open contours, strokes and self-intersecting input.

## Test plan (to run with the first APK)
Cases: circle ∩ star, rectangle − circle (hole), two tangent circles, two coincident edges, self-intersecting bow-tie,
EvenOdd and NonZero inputs, 500-anchor inputs. For each: success or failure, anchor count of the result, maximum distance
between the result outline and a rasterised reference, time on the reference phone.

## Default
Option A (`Path.op` behind the `BooleanOps` interface, implemented in a conditional `geometry-android` module), unless the
test plan shows unacceptable failures, in which case option B (a JVM polygon library plus curve refit).
