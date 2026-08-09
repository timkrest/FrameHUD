# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `FrameHudEvent.FirstFrame` reports how long each Activity instance took from before `onCreate` to
  its first displayed frame, on API 29 and newer. First draws remain excluded from jank and
  percentile statistics.

## [0.5.0] - 2026-08-09

### Added

- `FrameHud.mark` attributes frames to an interaction instead of to whichever activity has focus.
  The header names it in place of the timing, every event carries it, and clearing it reports a
  `FrameHudEvent.MarkEnded` covering that stretch alone. Leaving the screen clears it.
- `JankThresholds.maxDroppedReports` — dropped `FrameMetrics` reports fail the gate. They
  undersample every other figure it checks, so a lossy run could pass on numbers nobody collected.
  Defaults to zero.

### Changed

- `FrameHud.vsyncRate` is now `choreographerTicksPerSecond`, and the matching
  `JankDiagnosis.of` parameter follows it. The old name looked like the display refresh rate; this
  value is the number of Choreographer ticks the main thread handled per second.
- `FrameHudEvent` gained `mark`, and `JankBurst`, `FrozenFrames` and `ThermalChanged` take it in the
  constructor. Only the library emits them, so this shows up in your code only if you build events
  by hand in tests.
- `FrameHudConfig.metricsSampleWindowSize` is now `metricsSampleWindowFrames`, with the default
  constant renamed to match. The unit belongs in the name, as it already is one line below.
- `JankCause.Stage.avgMs` is now `averageMs`, spelled like `MetricValue.average`.
- `FrameHud` carries `@MainThread`, `@AnyThread` and `@WorkerThread`, so lint reports a call made from
  the wrong thread instead of leaving it to the runtime check. `framehud-noop` mirrors them.
- `FrameHistory` carries the deadline each frame had beside its duration, so `get(index)` gave way
  to `totalMsAt(index)` and `deadlineMsAt(index)`. A frame can now be judged against the deadline it
  actually had rather than against the one in force at the time of reading.
- `FrameHudConfig` rejects a window of no frames, a negative throttle and a fallback refresh rate
  that is not finite and positive, instead of quietly clamping them once the collector got that far.
  The artifact is `debugImplementation` only, so the throw lands in the build that wrote the value.

### Fixed

- The refresh rate was read off the view hierarchy from the metrics thread every frame. `View.getDisplay`
  is UI-thread only; the `Display` is now taken when the window is bound and read from there.
- Events raised after leaving a screen still carried its name instead of null.
- The `over` row stayed green until a frame lost a whole extra budget. It was coloured as a frame
  time rather than as the overrun it holds.
- The panel could land off-screen after a rotation, with no way to drag it back. Its position was
  clamped to the display only while dragging, never when the display changed underneath it.
- The collapsed sparkline covered half a second where the figures printed beside it covered the whole
  window. It dropped the frames it had no room for; now each bar keeps the worst frame of its slice,
  so both span the same window and a spike survives however narrow the chart is.
- One slow frame flattened the sparkline for as long as it stayed in the window, and moved the budget
  line with it. The scale now climbs in doublings of the budget and stops at sixteen.
- Sparkline bars stretched to fill the width and narrowed as the window filled. They now keep a slot
  width and fill from the right.

## [0.4.0] - 2026-08-02

### Added

- `SessionStats.FROZEN_FRAME_MS` — the threshold Play Vitals counts as a frozen frame, so a caller
  reading `frozenFrames` can name the same number the library uses.

### Fixed

- Dragging the panel slowly moved it nowhere. Each pointer event was rounded to whole pixels on its
  own, so on a high-polling-rate screen every step rounded to zero and the fraction was lost.

## [0.3.0] - 2026-08-01

### Changed

- The Maven coordinates are `com.timkrest` and the packages are `com.timkrest.framehud`, replacing
  `io.github.timkrest` in both. Update the dependency lines and the imports; a manifest override that
  names the installer takes `com.timkrest.framehud.FrameHudInstaller`. 0.1.0 and 0.2.0 stay published
  under the old coordinates and are not republished under the new ones.
- `framehud-noop` left the documented install. `debugImplementation` already keeps the artifact, its
  provider and the `SYSTEM_ALERT_WINDOW` it declares out of a release build, so the no-op is needed
  only where app code references `FrameHud` outside `src/debug`.

### Fixed

- Split-screen on Android 10 and up resumes every visible activity, so the panel could measure a
  window other than the one being used. It follows the focused window instead.

## [0.2.0] - 2026-07-31

### Added

- On an emulator the panel shows an `EMU` badge, labels the render-thread and GPU sections `· host`
  and greys their rows out, since they measure the host GPU rather than a device. The verdict picks
  from the phases the app is responsible for instead. Collection and events are unchanged, so jank
  gates in instrumentation tests keep working on CI emulators.

### Changed

- `PerformanceMetrics` is grouped into `phases`, `window`, `session` and `display` instead of 22 flat
  fields. Read `metrics.phases.draw`, `metrics.window.fps`, `metrics.display.frameBudgetMs`.
- `cpu`, `render`, `other`, `bottleneck` and `bottleneckStage` are derived from the phase timings
  rather than stored alongside them, so they can no longer disagree with the phases they sum up.
- `MetricValue.peak` is now nullable: zero could not stand in for "no peak" once `overrun` started
  reporting the headroom of a frame that finished early, which is negative.
- `DEFAULT_REFRESH_RATE_HZ` moved from `PerformanceMetrics` to `DisplayInfo`, and
  `effectiveRefreshRate` is gone — `DisplayInfo.refreshRateHz` is always positive.
- `framehud-api` depends on Compose's stability annotations instead of the whole Compose runtime, so
  a release build on `framehud-noop` no longer drags Compose into an app that does not use it.

### Fixed

- `JankAssertions.assertNoJank()` and `DetectJankAfterTestSuccess` failed with "FrameHud is not
  collecting" in their intended use: the rule asserts after the test body, by which point
  `ActivityScenario` has closed the activity and stopped the panel. Session aggregates now survive
  the panel being torn down, and are cleared by `FrameHud.reset()` as before.
- A screen summary event could be labelled with the next screen when activities swapped faster than
  the metrics thread drained its queue.
- Leaving a screen blocked the main thread until the metrics thread had drained its queue — which
  includes your event listeners. The thread now outlives a single screen and is never waited on from
  `onPause`.
- An unexpected failure while reading frame metrics is logged instead of reaching the platform's
  uncaught handler, which took the whole app down with the overlay.
- A hidden panel kept collecting and recomposing. Its lifecycle now follows its visibility, so
  readings stop while it is off screen.
- Frames longer than the widest histogram bucket landed in no bucket at all, which pulled session
  percentiles up. They are counted in the last one instead.
- The `over` row lost its peak column until a frame missed its deadline, because a peak of zero read
  as "no peak" for a timing that is negative while frames finish early.

## [0.1.0] - 2026-07-30

### Added

- Frame timing panel with per-stage breakdown (input, animation, layout, draw, sync, command issue,
  buffer swap, GPU), rendered in its own window so it stays out of the measured window's metrics.
- Rolling window and session aggregates: jank share, p50/p95/p99, frozen frames, longest jank
  streak, dropped `FrameMetrics` reports.
- Heap, GC and thermal sampling alongside frame metrics.
- Bottleneck detection across the CPU, render and GPU stages, surfaced as a verdict line.
- `framehud-noop`, an API-compatible no-op for release builds.
- Zero-config install: a `ContentProvider` starts the panel in the main process and follows the
  resumed activity. Remove it with `tools:node="remove"` and call `FrameHud.install(application)`
  instead.
- Runtime configuration: assign a copy to `FrameHud.config` and it applies immediately.
- `JankDiagnosis`, which blames jank on thermal throttling, GC pauses, vsync starvation, a late frame
  start or the bottleneck stage, whichever fires first.
- `FrameHudEventListener` with jank burst, frozen frame, thermal change and per-screen summary
  events, each labelled with the activity it came from. `LogcatEventListener` is on by default.
- `framehud-instrumentation`: `DetectJankAfterTestSuccess`, `JankAssertions.assertNoJank()`,
  `JankThresholds` and `@SkipJankDetection` for failing instrumentation tests on jank.
- `FrameHud.awaitSessionStats()`, a blocking snapshot of the session for tests.

[Unreleased]: https://github.com/timkrest/FrameHUD/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.5.0
[0.4.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.4.0
[0.3.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.3.0
[0.2.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.2.0
[0.1.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.1.0
