# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/com.timkrest/framehud)](https://central.sonatype.com/artifact/com.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![API 24+](https://img.shields.io/badge/API-24%2B-brightgreen)](https://developer.android.com/tools/releases/platforms)

[English](README.md) · [Русский](README.ru.md)

A draggable debug panel that shows how long each stage of a frame takes, and which one is slowing
you down.

<img src="docs/panel.png" alt="The panel over the sample app while scrolling a list" width="420">

- **A row per stage.** `input`, `anim`, `layout`, `draw` on the main thread, then `sync`, `command`,
  `swap` on the render thread, and `gpu`
- **Says why frames drop.** Thermal throttling, GC pauses, too few Choreographer ticks, or a late start
- **Keeps the case, not just the number.** A jank burst or a frozen frame is saved with the frames
  around it and the readings of that moment, and with the stack the main thread stood in when it was
  the one stuck
- **Measures your app, not itself.** The panel draws in its own window
- **Fails tests on jank.** A JUnit rule with thresholds
- **Compares with earlier runs.** A baseline per device, so a gate checks the delta instead of a
  fixed number
- **Works without the panel.** `framehud-metrics` collects and reports with no window and no
  permission
- **Made for QA runs.** adb commands, JSON and HTML session exports, sections and counters in a
  system trace
- **Nothing in release builds.** `debugImplementation` leaves out the panel, its provider and the
  `SYSTEM_ALERT_WINDOW` it declares

## Quick start

```kotlin
dependencies {
    debugImplementation("com.timkrest:framehud:0.18.1")
}
```

There is nothing to call. A `ContentProvider` starts the panel, and it follows whichever activity
has focus.

Requires `minSdk` 24. Frame phases come from `FrameMetrics`; GPU timings need API 31+ and a driver
that reports them.

`debugImplementation` already keeps everything out of a release build. Add `framehud-noop` only if
you call `FrameHud` outside `src/debug`, because a release build still has to compile those lines.
It mirrors the API with empty bodies:

```kotlin
releaseImplementation("com.timkrest:framehud-noop:0.18.1")
```

## What the panel shows

```
ui 59/s · 16.7ms   58 FPS
⚠ layout 8.4 ms
CPU        now   avg  peak
input      0.1   0.2   1.1
anim       0.3   0.4   2.0
layout     7.9   8.4  22.3 ◀
draw       1.2   1.4   6.7
RENDER
sync       0.4   0.5   1.9
command    0.6   0.7   3.1
swap       0.2   0.3   1.4
GPU
gpu        2.1   2.4   9.8
delay      0.3   0.4   2.2
other      0.3   0.3
TOTAL     11.3  12.6  38.6
over      -5.4  -4.1  21.9
pipe:cpu   9.5  10.4
win  jank  7.5%  p95  18.4  max  22.3
ses  p50  11.8  p95  19.6  p99  28.4
ses 4312f 1m12s jank 6.4% frz0 run3
lost 2.1s
mem 84/256 ▲96 · nat 37 ▲41 MB
gc x3 · 18 ms
therm none · hr 0.68
cpu 62% ▲140 · pss 214 ▲240 MB
thr 38 ▲44 · fd 210 ▲260
```

The header shows the main thread's Choreographer tick rate, the frame budget and FPS. The verdict
under it names the row to look at, and `◀` marks that row. Columns read `now avg peak`: the current
frame, the average over the window, and the peak since the last reset. A tap steps through the three
views — every row, the frame rows on their own, and one line; holding freezes the readings, and `▤`
switches to [the worst screens](docs/guide.md#screen-history) and back.

[Reading the panel](docs/metrics.md) explains every row and what to do when one turns red.

## Fail tests on jank

```kotlin
androidTestImplementation("com.timkrest:framehud-instrumentation:0.18.1")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

Thresholds can be fixed, as above, or [relative to a baseline](docs/guide.md#fail-tests-on-jank)
of earlier runs on the same device.

## Sample

```
./gradlew :sample:installDebug
```

**Load** stresses the frame pipeline with six toggles, **Readouts** shows every reading taken from
the flows instead of the panel, and **Session** is what a QA run ends with: baselines, past runs,
incidents, export.

## Documentation

- [Guide](docs/guide.md): collection without the panel, configuration, events and incidents, the
  Perfetto flight recorder, screens and marks, exports and adb, baselines and the jank gate
- [Reading the panel](docs/metrics.md): what every row means, how to measure a screen, and what to
  do when something turns red
- [Comparing the tools](docs/comparison.md): how FrameHUD differs from JankStats, Macrobenchmark,
  Perfetto and Play Vitals
- [API reference](https://javadoc.io/doc/com.timkrest/framehud-metrics): generated from the sources
  of each release
- [Changelog](CHANGELOG.md): what changed in each release
- [Contributing](CONTRIBUTING.md): how to build, and what to check before opening a pull request.
  Contributions are covered by a [CLA](CLA.md), which a bot will ask you to sign.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
