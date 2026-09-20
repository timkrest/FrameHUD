# FrameHUD

[English](index.md) · [Русский](index.ru.md)

A debug panel over your running app that breaks every frame into its stages and names the one that
is slowing you down. It installs as `debugImplementation` and leaves nothing in a release build:
[source and quick start](https://github.com/timkrest/FrameHUD).

- [Guide](guide.md): collection without the panel, configuration, events and incidents, the Perfetto
  flight recorder, screens and marks, exports and adb, baselines and the jank gate
- [Reading the panel](metrics.md): what every row means, how to measure a screen, and what to do
  when something turns red
- [Comparing the tools](comparison.md): how FrameHUD differs from JankStats, Macrobenchmark,
  Perfetto and Play Vitals
- [A jank percentage doesn't say where the frame went](article-frame-phases.md): two rendering bugs
  that a frame counter reports as the same, measured on a Galaxy S25 Ultra
