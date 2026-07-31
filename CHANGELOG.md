# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- The Maven coordinates are `com.timkrest` and the packages are `com.timkrest.framehud`, replacing
  `io.github.timkrest` in both. Update the dependency lines and the imports; a manifest override that
  names the installer takes `com.timkrest.framehud.FrameHudInstaller`. 0.1.0 and 0.2.0 stay published
  under the old coordinates and are not republished under the new ones.

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

[Unreleased]: https://github.com/timkrest/FrameHUD/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.2.0
[0.1.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.1.0
