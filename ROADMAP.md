# Roadmap

[English](ROADMAP.md) · [Русский](ROADMAP.ru.md)

Roughly the order things are likely to happen in. There are no dates, and the order follows what
people ask for. Open an issue if something here matters to you, or if what you need is missing.

## Toward 1.0

Before 1.0 the public API can change in a minor release; the changelog says what to write instead.
1.0 fixes it: binary compatibility holds within a major version, and anything that goes away is
deprecated for a release first.

## Next

- **`framehud-metrics`** — collection without the panel: the same events and session stats, no window,
  no `SYSTEM_ALERT_WINDOW` in the merged manifest. For QA and CI builds that should report without
  drawing.
- **Screen names** — the app names the current screen with a route like `product/{id}`, not
  `product/12345`, and that name replaces the activity class. Setting a new one closes the stats of
  the previous screen and starts the next; the window stays bound. Without this a single-activity
  app is one screen for the whole session.
- **adb control** — `enable`, `disable`, `reset`, `mark`, `screen` and `export` over a broadcast.
  Only debug builds register the receiver. A QA build starts reporting, marks a scenario and pulls
  the result without a rebuild.
- **Session export** — JSON and a self-contained HTML report with stats, the frame window, worst-frame
  timestamps, context, device, API, app version, refresh rate and measurement state. The app may add
  its own diagnostic files; the result goes to CI or the system share sheet, but is never uploaded
  automatically.
- **Measurement context** — a few `key=value` pairs next to the screen and `mark`: UI variant, action,
  test scenario. Events and exports retain the state in which jank happened, as JankStats does.
- **A Perfetto track** — screens and marks as trace sections, jank and frozen frames as events, and
  p95, jank and memory as counters. A normal system trace gains FrameHUD context and Macrobenchmark
  can measure named sections; FrameHUD does not record the trace itself.

## Later

- **Measurement confidence** — mark a report suspect after dropped `FrameMetrics`, throttling, a
  refresh-rate change, an emulator, low battery, a slow listener or a sample that is too short.
  Counted per metric: an emulator says little about GPU timings but measures main-thread jank fine.
  The jank gate gets a third result, inconclusive: strict CI fails it with its own reason, a warn
  mode only reports it. Baselines refuse to compare incompatible conditions.
- **Lost time** — sum positive overrun per screen and mark. The jank percentage counts 18 ms and
  300 ms frames as one bad frame each; lost time keeps the difference.
- **Baselines** — compare p50, p95, jank, lost time and frame phases per screen and mark with earlier
  exported runs from the same device, API and display mode. The baseline is a file: CI stores it and
  passes it to the next run, the device keeps nothing. The report shows the delta, how many runs are
  affected and which phase regressed most. A fixed threshold that fits one device is flaky on the
  next — the usual reason a jank gate ends up switched off.
- **First usable frame** — the app says when its data is ready and measurement ends on the next
  displayed frame, so a quickly drawn skeleton no longer counts as a ready screen. On startup the
  signal comes from `FullyDrawnReporter`: the `ReportDrawnWhen` an app already has for
  Macrobenchmark is enough. Screens after that need their own call — `reportFullyDrawn` only covers
  the launch.
- **Incident snapshot** — on a jank burst or frozen frame, keep a short window of frames around the
  event, together with context, memory, GC, thermal state and dropped reports. QA saves the actual
  case instead of trying to reproduce it from memory.
- **Incident grouping** — combine matching snapshots by screen, mark, diagnosis and environment. A
  report says “layout jank happened 7 times” and keeps the worst case instead of seven duplicates.
- **Screen history** — the last reports kept in memory, ranked by frozen frames, jank, p95 and sample
  size, and opened from the panel. Walk through the app first, then inspect the worst screens instead
  of reading each one live.
- **Process health** — occasional samples of app CPU, PSS, thread count and open files, with peaks.
  Steady growth shows up in the incident snapshot; the panel does not turn into a system monitor.
- **More than one window** — dialogs and secondary displays measured on their own, not just the
  resumed activity.

## Considering

Nothing designed yet. Whether these happen depends on feedback.

- History that survives a restart, so yesterday's numbers are still around
- A Perfetto flight recorder: QA starts a ring-buffer trace over adb, then jank or a frozen frame asks
  it to retain the last few seconds and a little time after the event
- A Macrobenchmark bridge: reset, marks and export around a scenario, with FrameHUD results next to
  the standard JSON and trace, but no home-grown benchmark runner
- App-defined counters next to frame data and in Perfetto, such as an image decode queue or cache
  misses
- Different budgets per screen and mark: a scroll, a transition and a heavy debug screen need not
  share one global threshold
- A blocked-main-thread stack: a watchdog starts occasional samples only after a delay and attaches
  recurring stack frames to the incident snapshot
- Recomposition counts next to the frame phases: with `FrameMetrics` alone a screen that recomposes
  everything just looks slow; needs a stable Compose API — `ObservableComposition` is still
  experimental — and would be an opt-in `framehud-compose` module
- Wear OS and TV

## Not planned

- The panel in release builds. It is a debug tool, and `framehud-noop` exists so release code compiles
  without it.
- FPS in a notification. An app in the background draws no frames, so there is nothing to show. When
  the panel covers what you are testing, collapse it or read logcat.
- Uploading anything anywhere. Measurements stay on the device.
- Startup timing and production aggregates. Macrobenchmark measures a cold start, Play Vitals and
  Firebase Performance report on an installed base; FrameHUD measures the session in front of you.
- A system profiler or trace viewer of its own. FrameHUD leaves markers and may signal an event, but
  Perfetto records and analyzes the trace.
- Rendering outside the View and Canvas pipeline. `FrameMetrics` reports nothing for Vulkan, OpenGL or
  a game engine, so neither can FrameHUD.
- A Gradle plugin for the jank gate. It would wire `debugImplementation` and `releaseImplementation`
  per variant and save a `@get:Rule` line per module, but following AGP across versions costs more
  than both are worth.
- Other platforms. Frame phases come from `FrameMetrics`, which is Android and nothing else.
