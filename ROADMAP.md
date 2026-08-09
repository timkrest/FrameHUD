# Roadmap

[English](ROADMAP.md) · [Русский](ROADMAP.ru.md)

Roughly the order things are likely to happen in. There are no dates, and the order follows what
people ask for. Open an issue if something here matters to you, or if what you need is missing.

## Toward 1.0

Before 1.0 the public API can change in a minor release; the changelog says what to write instead.
1.0 fixes it: binary compatibility holds within a major version, and anything that goes away is
deprecated for a release first.

## Next

- **Interaction marks** — `FrameHud.mark("scroll")`, so a reading belongs to a gesture rather than to
  whichever activity had focus. Screen summaries answer which screen is slow; marks answer which
  interaction is.
- **First frame of a screen** — how long a screen takes from creation to its first frame. Not process
  startup: `FrameMetrics` marks the first frame of a window layout, which happens again on rotation
  and window recreation.
- **`framehud-metrics`** — collection without the panel: the same events and session stats, no window,
  no `SYSTEM_ALERT_WINDOW` in the merged manifest. For QA and CI builds that should report without
  drawing.
- **Where FrameHUD fits** — a page placing it next to JankStats, Macrobenchmark, Perfetto and Play
  Vitals, and what question each of them answers.

## Later

- **Baselines** — fail when p95 regresses against a stored run rather than against a fixed percentage.
  A threshold that fits one device is flaky on the next, which is the usual reason a jank gate ends up
  switched off.
- **Session export** — session stats and the frame window as JSON, with trace markers, so a bad stretch
  opens in Perfetto next to the rest of the system.
- **adb control** — `show`, `hide`, `reset` and threshold changes over a broadcast. A QA build starts
  reporting without a rebuild.
- **Screen history** — the last reports kept in memory and opened from the panel. Walk through the app
  first, compare screens afterwards, instead of reading each one live.
- **Notification** — FPS and jank in a notification, for when the app sits in the background or the
  panel covers what you are testing.
- **Recomposition counts** — next to the frame phases. With only `FrameMetrics` to go on, a screen that
  recomposes the world looks exactly like a slow one.
- **More than one window** — dialogs and secondary displays measured on their own, not just the
  resumed activity.

## Considering

Nothing designed yet. Whether these happen depends on feedback.

- History that survives a restart, so yesterday's numbers are still around
- Macrobenchmark integration
- Wear OS and TV

## Not planned

- The panel in release builds. It is a debug tool, and `framehud-noop` exists so release code compiles
  without it.
- Uploading anything anywhere. Measurements stay on the device.
- Startup timing and production aggregates. Macrobenchmark measures a cold start, Play Vitals and
  Firebase Performance report on an installed base; FrameHUD measures the session in front of you.
- Rendering outside the View and Canvas pipeline. `FrameMetrics` reports nothing for Vulkan, OpenGL or
  a game engine, so neither can FrameHUD.
- A Gradle plugin for the jank gate. It would wire `debugImplementation` and `releaseImplementation`
  per variant and save a `@get:Rule` line per module, but following AGP across versions costs more
  than both are worth.
- Other platforms. Frame phases come from `FrameMetrics`, which is Android and nothing else.
