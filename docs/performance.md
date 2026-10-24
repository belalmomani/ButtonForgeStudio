# Performance plan and decisions

Targets (provisional, from PROJECT_STRUCTURE.md section 6): p95 frame time <= 16.6 ms at 100 and 500 points with 10 layers,
<= 33 ms at 1000 points; undo/redo <= 50 ms; open/save <= 1 s at 1000 points and 50 layers.

## Measurements so far
None. `gradle :integration-tests:perfTest` (workflow "Perf baseline (JVM)") prints how the core operations scale on a CI machine.
It is not a phone measurement. Device frame timing needs the Macrobenchmark module, which is not created yet.

## Decision gates (add complexity only when a measurement crosses the line)
| Technique | Added only if |
|---|---|
| Spatial index for hit testing | p95 hit test > 4 ms at 1000 points |
| Cache of converted `android.graphics.Path` | path conversion > 20% of frame time |
| Partial redraw (dirty rect) | targets still missed after caching |
| Cheaper drag updates (avoid rebuilding the whole contour per event) | drag event cost at 1000 points / 50 layers exceeds the frame budget |

Known cost to watch: `EditorSession.updateAnchorDrag` rebuilds every anchor of the dragged shape on each event (O(points)).
