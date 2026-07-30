# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.1.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.1.0
