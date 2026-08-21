# Roadmap

[English](ROADMAP.md) · [Русский](ROADMAP.ru.md)

Roughly the order things are likely to happen in. There are no dates, and the order follows what
people ask for. Open an issue if something here matters to you, or if what you need is missing.

## Toward 1.0

Before 1.0 the public API can change in a minor release; the changelog says what to write instead.
1.0 fixes it: binary compatibility holds within a major version, and anything that goes away is
deprecated for a release first.

## Next

Nothing queued. What lands next comes out of the list below and out of what people ask for.

## Considering

Nothing designed yet. Whether these happen depends on feedback.

- History that survives a restart, so yesterday's numbers are still around
- A Perfetto flight recorder: QA starts a ring-buffer trace over adb, then jank or a frozen frame asks
  it to retain the last few seconds and a little time after the event
- A Macrobenchmark bridge: reset, marks and export around a scenario, with FrameHUD results next to
  the standard JSON and trace, but no home-grown benchmark runner
- App-defined counters next to frame data and in Perfetto, such as an image decode queue or cache
  misses
- A blocked-main-thread stack: a watchdog starts occasional samples only after a delay and attaches
  recurring stack frames to the incident snapshot
- Recomposition counts next to the frame phases: with `FrameMetrics` alone a screen that recomposes
  everything just looks slow. Needs a stable Compose API, and `ObservableComposition` is still
  experimental. Would be an opt-in `framehud-compose` module
- Wear OS and TV

## Not planned

- The panel in release builds. It is a debug tool, and `framehud-noop` exists so release code compiles
  without it.
- FPS in a notification. An app in the background draws no frames, so there is nothing to show. When
  the panel covers what you are testing, collapse it or read logcat.
- Uploading anything anywhere. Measurements stay on the device.
- Process startup and production aggregates. FrameHUD times the screen in front of you, first frame
  and usable frame included; a cold start belongs to Macrobenchmark, and an installed base to Play
  Vitals and Firebase Performance.
- A system profiler or trace viewer of its own. FrameHUD leaves markers and may signal an event, but
  Perfetto records and analyzes the trace.
- Rendering outside the View and Canvas pipeline. `FrameMetrics` reports nothing for Vulkan, OpenGL or
  a game engine, so neither can FrameHUD.
- A Gradle plugin for the jank gate. It would wire `debugImplementation` and `releaseImplementation`
  per variant and save a `@get:Rule` line per module, but following AGP across versions costs more
  than both are worth.
- Other platforms. Frame phases come from `FrameMetrics`, which is Android and nothing else.
