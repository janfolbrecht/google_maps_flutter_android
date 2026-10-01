# Fork of google_maps_flutter_android for RedMap

Base: `google_maps_flutter_android` 2.19.13 as published on pub.dev (the first commit of this
repository). The package version is unchanged, so `google_maps_flutter` and the platform interface
resolve as before. RedMap pins a commit SHA of this repository in `dependency_overrides`.

## What is different

| Change | Switch | What it does |
|---|---|---|
| Icon cache | `debug.fgm.opt.icon_cache` | One `BitmapDescriptor` per distinct marker icon (`FgmIconCache`). Upstream opens, decodes and scales the bitmap for every marker and hands each one to the Maps SDK as a bitmap of its own; a map of equal pins then pays the decoding on the platform thread per marker and holds a texture per marker. |
| Measurement | `debug.fgm.perf` | `[FGMPerf] batch` line per `updateMarkers` call with its phases, `[FGMPerf] hitch` line per gap of 34 ms or more between two frames of the platform thread (`FgmPerf`). Off by default. |

Switches are Android system properties read once per process:

```
adb shell setprop debug.fgm.opt.icon_cache 0   # the optimisation off: upstream's behaviour
adb shell setprop debug.fgm.perf 1             # measurement on
adb shell am force-stop <package>              # read at the next start
```

An optimisation is on unless its property is `0`. With `icon_cache` off and `perf` unset the
plugin does what upstream does.

## What an app sees

Nothing different on the map. Markers given equal icons (same asset or same bytes, same size,
scaling and pixel ratio) share one descriptor for as long as their map lives; the cache holds at
most 256 icons per map, least recently used first out. Icons of other kinds (default marker,
legacy asset and bytes, pin config) are made as before.

## Measured (RedMap, 5000-school test country)

Pixel 9, release build, big pan at a cap of 1000 pins: the batch held the platform thread for
126 ms and the longest gap between two frames was 141 ms; with the cache 18 ms and 39 ms. One
marker operation 0.094 ms against 0.015 ms. Memory with 5000 pins on the map, 20 s in the
background: Graphics 451 MB against 125 MB, Private Other 353 MB against 34 MB. The full tables
are in RedMap's `.AI/perf/android-markers.md`.

## Tests

`android/src/test/java/io/flutter/plugins/googlemaps/FgmIconCacheTest.java`. Run from an app that
depends on the fork: `./gradlew :google_maps_flutter_android:testDebugUnitTest`.

## Maintenance

Rebase onto a newer upstream release only when a Flutter or Maps SDK release forces it: copy the
new release over the first commit's tree and reapply the commits above it. The changes touch
`Convert.java` (two call sites and one field), `GoogleMapController.updateMarkers`,
`MarkersController` (timing only) and add `FgmIconCache.java`, `FgmPerf.java`.
