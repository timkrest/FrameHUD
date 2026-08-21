# FrameHUD and other Android tools

[English](comparison.md) · [Русский](comparison.ru.md)

FrameHUD is for the first pass: open a screen and immediately see its jank, p95 and slow frame
stage. The other tools answer different questions. They confirm a regression, find its exact cause,
or show its impact after release.

## What to use and when

| Tool | When it helps |
| --- | --- |
| **FrameHUD** | Manual development and QA. Shows FPS, jank, percentiles and frame stages over the running app. |
| **[JankStats](https://developer.android.com/topic/performance/jankstats)** | Custom per-frame collection. Provides duration, a jank flag and UI state; the app owns storage and aggregation. |
| **[Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)** | Regression checks on CI. Repeats a scenario against a release-like build and reports P50/P90/P95/P99, overrun and a trace for every iteration. |
| **[Perfetto](https://perfetto.dev/docs/data-sources/frametimeline)** | Detailed analysis of a bad frame. Puts app threads, scheduling, Binder, GC, RenderThread, GPU and SurfaceFlinger on one timeline. |
| **[Play Vitals](https://support.google.com/googleplay/android-developer/answer/9844486?hl=en)** | Post-release statistics: slow and frozen frames, percentiles and affected sessions across real devices. |

FrameHUD also compares a run with a baseline of earlier runs on the same device, which catches a
regression during a QA pass. That is not a replacement for Macrobenchmark, which repeats a fixed
scenario against a release-like build, while FrameHUD measures whatever the run happened to do.

JankStats is the closest match to FrameHUD: on API 24+ both use `FrameMetrics`. The difference is
how much is already built. JankStats emits every frame; FrameHUD turns those frames into a panel,
screen and session statistics, events and a jank gate for instrumentation tests. `framehud-metrics`
drops the panel and keeps the rest, which is the closest FrameHUD gets to JankStats.

## Why the numbers may differ

- FrameHUD compares a frame with the system deadline, JankStats uses its own heuristic, and Perfetto
  checks the actual presentation time.
- Macrobenchmark CPU duration, FrameHUD total duration and Perfetto presentation time are different
  quantities.
- FrameHUD measures a manual pass, Macrobenchmark measures repeated iterations, and Play Vitals
  combines many devices and sessions.
- Frame budget depends on refresh rate: 16.7 ms at 60 Hz and 8.3 ms at 120 Hz. `frameBudgetsMs`
  replaces it with a number you pick, which no other tool knows about.
- “First frame” also differs: Macrobenchmark measures process startup; FrameHUD measures a new
  Activity instance, including screen recreation.
- Time to usable is the closer pair: on a launch it ends on the same `reportFullyDrawn` signal as
  Macrobenchmark's time to full display, but during a manual pass instead of a repeated cold start.
  On every other screen the app calls `FrameHud.reportUsable()`, and where it calls it is up to it.

Do not directly compare FrameHUD P95 with Macrobenchmark P95. Watch how one metric moves in one
tool under the same conditions.

## How they work together

1. FrameHUD finds the problem and points at a suspicious stage.
2. Macrobenchmark confirms the regression in a repeatable scenario.
3. Perfetto finds the specific cause; the recorded trace already carries FrameHUD's screens and
   marks as `framehud:*` sections, next to its own counters and the ones the app kept. A
   ring-buffer trace need not be stopped by hand at the right moment either, because an incident
   asks it to keep the seconds around itself.
4. Play Vitals shows whether users are affected.

JankStats sits outside this sequence: use it when the app needs custom performance telemetry.

FrameHUD invents no timings. Android reports most of them through `FrameMetrics`, and FrameHUD puts
them where you look while you work. Precise benchmarks, system-level investigation and production
statistics stay separate jobs.
