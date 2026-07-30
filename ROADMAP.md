# Roadmap

[English](ROADMAP.md) · [Русский](ROADMAP.ru.md)

Roughly the order things are likely to happen in. There are no dates, and the order follows what
people ask for. Open an issue if something here matters to you, or if what you need is missing.

## Next

- **`framehud-metrics`** — collection without the panel: the same events and session stats, no window,
  no `SYSTEM_ALERT_WINDOW` in the merged manifest. For builds that should report without drawing.
- **Screen history** — the last reports kept in memory and opened from the panel. Walk through the app
  first, compare screens afterwards, instead of reading each one live.
- **Notification** — FPS and jank in a notification, for when the app sits in the background or the
  panel covers what you are testing.

## Later

- **Session export** — session stats and the frame window as JSON, with trace markers, so a bad
  stretch opens in Perfetto next to the rest of the system.
- **Gradle plugin** — wires the jank gate into instrumentation tests. No hand-written rule in every
  module.
- **adb control** — `show`, `hide`, `reset` and threshold changes over a broadcast. A QA build starts
  reporting without a rebuild.
- **Recomposition counts** — next to the frame phases. With only `FrameMetrics` to go on, a screen that
  recomposes the world looks exactly like a slow one.
- **More than one window** — dialogs and secondary displays measured on their own, not just the resumed
  activity.

## Considering

Nothing designed yet. Whether these happen depends on feedback.

- History that survives a restart, so yesterday's numbers are still around
- Baselines: fail when p95 regresses against a stored run rather than a fixed threshold
- Macrobenchmark integration
- Wear OS and TV

## Not planned

- The panel in release builds. It is a debug tool, and `framehud-noop` exists so release code compiles
  without it.
- Uploading anything anywhere. Measurements stay on the device.
