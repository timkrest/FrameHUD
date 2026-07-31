# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/com.timkrest/framehud)](https://central.sonatype.com/artifact/com.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

[English](README.md) · [Русский](README.ru.md)

A draggable debug panel that breaks every frame down by pipeline stage and points at the stage that
limits your frame rate.

<img src="docs/panel.png" alt="The panel over the sample app while scrolling a list at 120 Hz" width="420">

Every stage gets its own row: `input`, `anim`, `layout` and `draw` on the main thread, then `sync`,
`command` and `swap` on the render thread, with `gpu` above them. Each row shows the current frame
and the average over the window; measured phases add the peak since the last reset.

The panel does more than print numbers. It names the stage the frame rate is stuck on, and when
frames are dropped it says why: thermal throttling, GC pauses, missing vsync ticks, or a frame that
started late. The panel gets a window of its own, so it stays out of the metrics of the window it
measures.

## Install

```kotlin
dependencies {
    debugImplementation("com.timkrest:framehud:0.3.0")
}
```

Requires `minSdk` 24. Frame phases come from `FrameMetrics`; GPU timings need API 31+ and a driver
that reports them.

There is nothing to call. A `ContentProvider` starts the panel in the main process, and it follows
whichever activity is resumed. `debugImplementation` keeps the artifact out of release builds
altogether, along with that provider and the `SYSTEM_ALERT_WINDOW` it declares.

Add `framehud-noop` when your own code references `FrameHud` from a source set that release builds
compile. It mirrors the API with empty bodies, so those call sites resolve while nothing is measured
and no window is added.

```kotlin
releaseImplementation("com.timkrest:framehud-noop:0.3.0")
```

On an emulator the render thread and the GPU belong to the host machine, so those rows describe your
desktop rather than a device. The panel marks the header `EMU`, labels those sections `· host` and
greys them out. Main-thread phases, jank and the session aggregates stay meaningful, which is what a
jank gate on CI reads.

## Configure

Settings live in one immutable config. Assign a copy and it takes effect at once.

```kotlin
FrameHud.config = FrameHud.config.copy(metricsSampleWindowSize = 240)
```

| Option | Default | What it controls |
| --- | --- | --- |
| `enabled` | `true` | While false, no window is added and no frames are collected. |
| `overlayMode` | `PREFER_SYSTEM` | `APP_WINDOW` keeps the panel inside the app window and never uses the permission. |
| `eventListeners` | `[LogcatEventListener]` | Who receives jank burst, frozen frame, thermal and screen summary events. |
| `metricsSampleWindowSize` | `120` | Frames the rolling window keeps, behind `avg` and the percentiles. |
| `metricsThrottleIntervalMs` | `400` | How often the panel may redraw. Lower values cost more to render. |
| `fallbackRefreshRateHz` | `60` | Refresh rate assumed when the display reports none. |
| `metricsThreadName` | `framehud-metrics` | Name of the collecting thread, as it shows up in traces. |

`show()`, `hide()` and `toggle()` are shortcuts for `enabled`. The panel is not the only way to read
the numbers: `FrameHud.metrics`, `memoryStats`, `thermalStats` and `vsyncRate` are plain `StateFlow`s.
A reading groups into `phases` (per-stage timings), `window` (fps, jank and p95 over the sampling
window), `session` (since the last reset) and `display` (refresh rate and frame budget).

## Jank events

Listeners are notified once per jank burst rather than once per frame, with the cause already
attributed. The default listener writes to logcat.

```kotlin
FrameHud.config = FrameHud.config.copy(
    eventListeners = listOf(
        LogcatEventListener,
        FrameHudEventListener { event -> analytics.log(event.summary) },
    ),
)
```

Events arrive on the metrics thread. Don't block it and don't touch views from it.

## Fail tests on jank

```kotlin
androidTestImplementation("com.timkrest:framehud-instrumentation:0.3.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

The rule resets the collector before each test and checks the thresholds once the test passes, so a
failing test keeps its own error. Session aggregates outlive the panel, so the check still has
numbers after `ActivityScenario` has closed the activity. To opt out, annotate a test or class with
`@SkipJankDetection`, or call `JankAssertions.assertNoJank("scroll")` at a point you choose.

## Overlay permission

With `SYSTEM_ALERT_WINDOW` granted, the panel lives in a system window and survives navigation
between screens. Without it the window belongs to the current activity, so the panel is torn down and
recreated on every screen change, and a `⧉` button shows up that opens the permission screen. The
library never opens it on its own. Set `overlayMode = APP_WINDOW` to stay in the app window and hide
that button.

<details>
<summary>Installing by hand instead of the provider</summary>

Drop the provider and call `FrameHud.install(application)` yourself:

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

- [Reading the panel](docs/metrics.md) — what every row means, how to measure a screen, and what to
  do when something turns red
- [API reference](https://javadoc.io/doc/com.timkrest/framehud) — generated from the sources of
  each release
- [Roadmap](ROADMAP.md) — what is planned next, and what is deliberately not
- [Contributing](CONTRIBUTING.md) — how to build, and what to check before opening a pull request.
  Contributions are covered by a [CLA](CLA.md), which a bot will ask you to sign.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
