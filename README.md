# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/com.timkrest/framehud)](https://central.sonatype.com/artifact/com.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![API 24+](https://img.shields.io/badge/API-24%2B-brightgreen)](https://developer.android.com/tools/releases/platforms)

[English](README.md) · [Русский](README.ru.md)

A draggable debug panel that shows how long each stage of a frame takes, and which one is slowing
you down.

<img src="docs/panel.png" alt="The panel over the sample app while scrolling a list at 120 Hz" width="420">

- **A row per stage.** `input`, `anim`, `layout`, `draw` on the main thread, then `sync`, `command`,
  `swap` on the render thread, and `gpu`
- **Says why frames drop.** Thermal throttling, GC pauses, too few Choreographer ticks, or a late start
- **Measures your app, not itself.** The panel draws in its own window
- **Fails tests on jank.** A JUnit rule with thresholds
- **Works without the panel.** `framehud-metrics` collects and reports with no window and no
  permission
- **Made for QA runs.** adb commands, JSON and HTML session exports, sections and counters in a
  system trace
- **Nothing in release builds.** `debugImplementation` leaves out the panel, its provider and the
  `SYSTEM_ALERT_WINDOW` it declares

## Quick start

```kotlin
dependencies {
    debugImplementation("com.timkrest:framehud:0.9.0")
}
```

There is nothing to call. A `ContentProvider` starts the panel, and it follows whichever activity
has focus.

Requires `minSdk` 24. Frame phases come from `FrameMetrics`; GPU timings need API 31+ and a driver
that reports them.

## What the panel shows

```
ui 118/s · 8.3ms  118 FPS
⚠ layout 8.4 ms
CPU       now   avg  peak
input     0.1   0.2   1.1
anim      0.3   0.4   2.0
layout    7.9   8.4  22.3 ◀
draw      1.2   1.4   6.7
RENDER
sync      0.4   0.5   1.9
command   0.6   0.7   3.1
swap      0.2   0.3   1.4
GPU
gpu       2.1   2.4   9.8
delay     0.3   0.4   2.2
other     0.1   0.2
TOTAL    13.2  14.5  38.6
over      4.9   6.2  30.3
pipe:cpu 10.4  11.0
win  jank  4.2%  p95  12.1  max  22.3
ses  p50   7.1  p95  12.4  p99  19.8
ses 4312f 1m12s jank 4.2% frz0 run3
lost 2.1s
mem 84/256 ▲96 · nat 37 ▲41 MB
gc x3 · 18 ms
therm none · hr 0.68
```

The header carries the main thread's Choreographer tick rate, the frame budget and FPS. The verdict
under it names the row to look at, and `◀` marks that row.

Columns read `now avg peak`: the current frame, the average over the window, and the peak since the
last reset. Rows summed from other rows stop after `avg`.

`over` is how far the frame ran past its budget. `pipe:` is the slowest stage. `win` is the window,
`ses` the session.

[Reading the panel](docs/metrics.md) explains every row and what to do when one turns red.

## Release builds

`debugImplementation` already keeps everything out of a release build. Add `framehud-noop` only if
you call `FrameHud` outside `src/debug`, because a release build still has to compile those lines:

```kotlin
releaseImplementation("com.timkrest:framehud-noop:0.9.0")
```

It mirrors the API with empty bodies. The calls compile, nothing is measured, no window is added.

## Collect without the panel

A build that has to report but must not draw takes `framehud-metrics` instead of `framehud`. It
collects the same numbers and sends the same events, but adds no window and no `SYSTEM_ALERT_WINDOW`
to the merged manifest.

```kotlin
qaImplementation("com.timkrest:framehud-metrics:0.9.0")
```

`FrameHud` is the same object, so the code around it stays as it is. `enabled` now switches
collection alone, `show()`, `hide()` and `toggle()` still mean `enabled`, and `overlayMode` is
ignored. `framehud` is this artifact plus the panel, so a debug build needs nothing else.

## Configure

Settings live in one immutable config. Assign a copy and it takes effect at once.

```kotlin
FrameHud.config = FrameHud.config.copy(metricsSampleWindowFrames = 240)
```

| Option | Default | What it controls |
| --- | --- | --- |
| `enabled` | `true` | While false, no window is added and no frames are collected. |
| `overlayMode` | `PREFER_SYSTEM` | `APP_WINDOW` keeps the panel inside the app window and never uses the permission. |
| `eventListeners` | `[LogcatEventListener]` | Who receives first-frame, jank, frozen-frame, thermal, screen and interaction events. |
| `metricsSampleWindowFrames` | `120` | How much history `avg` and the percentiles cover. |
| `metricsThrottleIntervalMs` | `400` | How often the panel may redraw. Lower values cost more to render. |
| `fallbackRefreshRateHz` | `60` | Refresh rate assumed when the display reports none. |
| `metricsThreadName` | `framehud-metrics` | Name of the collecting thread, as it shows up in traces. |

`show()`, `hide()` and `toggle()` are shortcuts for `enabled`.

The panel is not the only way to read the numbers. `FrameHud.metrics`, `memoryStats`, `thermalStats`
and `choreographerTicksPerSecond` are plain `StateFlow`s. A reading groups into `phases` (per-stage
timings), `window` (fps, jank and p95 over the sampling window), `session` (since the last reset) and
`display` (refresh rate and frame budget).

## First frame

`FrameHudEvent.FirstFrame` reports how long an Activity instance took to reach its first displayed
frame. The timer starts before `onCreate` and stops at the end of that frame. The default listener
writes `MainActivity: first frame in 123.4 ms` to logcat.

Android marks first draws as expectedly slow, so FrameHUD reports them separately and keeps them out
of the rolling and session jank statistics. An Activity recreation is a new measurement; returning
to an existing Activity is not.

This event needs API 29 or newer because its start timestamp comes from `onActivityPreCreated`,
which was added in API 29. On older versions first draws remain excluded from jank statistics, but
no `FirstFrame` event is sent.

## Jank events

You get one event per jank burst, not one per frame, with the cause already worked out. The default
listener writes to logcat.

```kotlin
FrameHud.config = FrameHud.config.copy(
    eventListeners = listOf(
        LogcatEventListener,
        FrameHudEventListener { event -> analytics.log(event.summary) },
    ),
)
```

Events arrive on the metrics thread. Don't block it and don't touch views from it.

## Naming screens

Stats split by screen, and a screen is its activity class by default. In a single-activity app that
leaves one screen for the whole session. Name the screen with a route instead:

```kotlin
navController.addOnDestinationChangedListener { _, destination, _ ->
    FrameHud.screen = destination.route
}
```

Use the pattern `product/{id}`, not `product/12345`, so every product page counts as one screen.
A new name closes the stats of the previous screen and starts the next; the window stays bound. The
name holds until the next assignment, so an app that names screens must name every screen it shows.
`null` returns to activity class names.

## Marking an interaction

A screen summary tells you which screen is slow. A mark tells you which interaction is.

```kotlin
FrameHud.mark = "scroll"
// the gesture runs
FrameHud.mark = null
```

Frames drawn while the mark is set belong to it. The header reads `▸ scroll` instead of the timing
and every event fired meanwhile carries the name; clearing the mark reports a `MarkEnded` whose
stats cover that stretch alone. The panel's own rows keep covering the usual window and session.
The header labels them, it does not narrow them.

Leaving or renaming the screen clears the mark for you, so a gesture never spills into the next
screen.

## Measurement context

`FrameHud.context` keeps a few `key=value` pairs next to the screen and the mark: a UI variant, an
action, a test scenario. Every event carries the pairs set at the moment it fired, and exports
retain them, so a report says where jank happened and under which conditions.

```kotlin
FrameHud.context = mapOf("variant" to "new_checkout")
```

Changing the context closes nothing; it only annotates what follows.

## Exporting a session

`exportSession` writes the session since the last reset as JSON and a self-contained HTML report,
and returns both files. Each carries the stats and the phase breakdown for the session and for the
current screen, the frame window, the worst frames with wall-clock timestamps, context, device, app
version, measurement state and confidence issues. They land in `framehud/` under the app's external
files directory, so CI pulls them without root; `shareSession` opens the system share sheet with
them. Nothing is ever uploaded.

```kotlin
val export = FrameHud.exportSession(timeoutMs = 5_000) ?: return
FrameHud.shareSession(activity, export)
```

```
adb pull /sdcard/Android/data/<package>/files/framehud/
```

The app may put its own diagnostic files next to the returned ones.

## Driving over adb

A debuggable build with `framehud-metrics` answers broadcasts, so a QA build starts reporting,
marks a scenario and pulls the result without a rebuild:

```
adb shell am broadcast -a com.timkrest.framehud.ENABLE <package>
adb shell am broadcast -a com.timkrest.framehud.SCREEN --es name "product/{id}" <package>
adb shell am broadcast -a com.timkrest.framehud.MARK --es name checkout <package>
adb shell am broadcast -a com.timkrest.framehud.CONTEXT --es scenario smoke <package>
adb shell am broadcast -a com.timkrest.framehud.EXPORT <package>
```

`DISABLE` and `RESET` complete the set. Omitting `--es name` clears the screen or the mark, and
`CONTEXT` without extras clears the context. `EXPORT` answers with the report's path in the
broadcast result, ready for `adb pull`. A build that is not debuggable ignores every command.

## In a system trace

While a system trace records, screens and marks show up as `framehud:screen:<name>` and
`framehud:mark:<name>` sections, a jank burst as `framehud:jank_burst`, and jank percent, p95,
frozen frames and heap use as `framehud.*` counters. Macrobenchmark's `TraceSectionMetric` measures
the named sections. FrameHUD leaves markers only; recording and analyzing the trace stays with
Perfetto.

## Lost time

`SessionStats.lostTimeMs` sums how far the late frames ran past their deadline. Jank percent counts
an 18 ms frame and a 300 ms frame as one bad frame each; lost time keeps the difference, so two
screens sitting at the same 5% jank stop looking alike.

Frames that finished with headroom add nothing. The panel adds a line for it once it is above zero,
both reports carry it per session and per screen, and `JankThresholds(maxLostTimeMs = 500f)` fails a
test on it.

## Where the time went

`SessionStats.phases` breaks a session, a screen or a mark down by pipeline phase, as
`PhaseAverages`: the milliseconds an average frame spent in layout, draw, swap buffers and the rest,
and the stage it was bound by.

The panel's phase rows cover the last `metricsSampleWindowFrames` frames, so a screen that stuttered
for two seconds can look fine again by the time you read them. These cover every frame the interval
collected. Both reports carry the breakdown per session and per screen, and the HTML report puts the
two side by side.

`PhaseAverages.gpu` is null until the driver reports GPU time, which needs API 31+.

## Measurement confidence

`SessionStats.confidence` lists what got in the way while the stats collected: dropped
`FrameMetrics` reports, an event listener that held the metrics thread, thermal throttling, power
save or a battery at 15% or below, a refresh-rate change, an emulator, a sample too short for its
percentiles. Each issue names the figures it taints. An emulator taints only the render-thread and
GPU phases. A refresh-rate change taints jank percent, lost time and streak. A short sample taints the
percentiles it cannot support: p99 below 300 frames, p95 and jank percent below 60, p50 below 20.
The rest taint every figure.

The JSON export carries the issues for the session and the screen, the HTML report lists them, and
`ScreenEnded`/`MarkEnded` summaries end with `(suspect measurement)`.

## Fail tests on jank

```kotlin
androidTestImplementation("com.timkrest:framehud-instrumentation:0.9.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

The rule resets the collector before each test and checks the thresholds after the test passes, so a
failing test keeps its own error. Session totals outlive the panel, so there are still numbers after
`ActivityScenario` closes the activity.

A threshold whose figure a confidence issue taints cannot pass or fail honestly, so the gate calls
the run inconclusive and reports both the figure and the issue. The default `OnInconclusive.FAIL`
fails the test, `OnInconclusive.WARN` logs the message and lets it pass. A violated threshold with
a clean figure fails in both modes.

To opt out, annotate a test or class with `@SkipJankDetection`, or call
`JankAssertions.assertNoJank("scroll")` at a point you choose.

## Overlay permission

With `SYSTEM_ALERT_WINDOW` granted, the panel lives in a system window and survives moving between
screens. Without it the window belongs to the current activity, so the panel is recreated on every
screen change, and a `⧉` button appears that opens the permission screen.

The library never opens it on its own. Set `overlayMode = APP_WINDOW` to stay in the app window and
hide that button.

<details>
<summary>On an emulator</summary>

The render thread and the GPU belong to the host machine, so those rows describe your desktop, not a
device. The panel marks the header `EMU`, labels those sections `· host` and greys them out.

Main-thread phases, jank and the session totals stay meaningful. That is what a jank gate on CI
reads.

</details>

<details>
<summary>Installing by hand instead of the provider</summary>

Drop the provider and call `FrameHud.install(application)` from `Application.onCreate()`. The panel
comes up with the next resumed activity, so a call made later skips the screen already open.

```xml
<provider
    android:name="com.timkrest.framehud.FrameHudInstaller"
    android:authorities="${applicationId}.framehud-installer"
    tools:node="remove" />
```

</details>

## Sample

```
./gradlew :sample:installDebug
```

A 300-row list with five toggles: blocking the main thread, overdrawing, allocating per row, nesting
layouts, churning garbage. Each one moves a different metric.

## Documentation

- [Reading the panel](docs/metrics.md): what every row means, how to measure a screen, and what to
  do when something turns red
- [Comparing the tools](docs/comparison.md): how FrameHUD differs from JankStats, Macrobenchmark,
  Perfetto and Play Vitals
- [API reference](https://javadoc.io/doc/com.timkrest/framehud-metrics): generated from the sources
  of each release
- [Roadmap](ROADMAP.md): what is planned next, and what is deliberately not
- [Changelog](CHANGELOG.md): what changed in each release
- [Contributing](CONTRIBUTING.md): how to build, and what to check before opening a pull request.
  Contributions are covered by a [CLA](CLA.md), which a bot will ask you to sign.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
