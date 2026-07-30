# Roadmap

[English](ROADMAP.md) · [Русский](ROADMAP.ru.md)

What is planned, roughly in order. No dates: the order follows what people actually ask for, so open an
issue if something here matters to you — or if something missing does.

## Next

- **`framehud-metrics`** — collection without the panel, for builds that should report but not draw:
  the same events and session stats, no window, no `SYSTEM_ALERT_WINDOW` merged into the manifest.
- **Screen history** — the last reports kept in memory and opened from the panel, so screens can be
  compared after a walk through the app instead of only watched live.
- **Notification** — FPS and jank in a notification, for when the app is in the background or the panel
  is in the way of what you are testing.

## Later

- **Session export** — session stats and the frame window as JSON, plus trace markers so a bad stretch
  can be opened in Perfetto next to the rest of the system.
- **Gradle plugin** — wires the jank gate into instrumentation tests, so CI fails on a regression
  without hand-written rules in every module.
- **adb control** — `show`, `hide`, `reset` and threshold changes over a broadcast, so a QA build starts
  reporting without a rebuild.
- **Recomposition counts** — next to the frame phases. A screen that recomposes the world looks the same
  as a slow one when all you have is `FrameMetrics`.
- **More than one window** — dialogs and secondary displays measured separately, instead of only the
  resumed activity.

## Considering

Not designed yet; feedback decides whether these happen at all.

- History that survives a restart, so yesterday's numbers are still there
- Baseline comparison: fail when p95 regresses against a stored run, not against a fixed threshold
- Macrobenchmark integration
- Wear OS and TV

## Not planned

- The panel in release builds. It is a debug tool — `framehud-noop` is there to keep release code
  compiling without it.
- Uploading anything anywhere. Measurements stay on the device.
