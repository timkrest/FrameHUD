# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/io.github.timkrest/framehud)](https://central.sonatype.com/artifact/io.github.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

[English](README.md) · [Русский](README.ru.md)

A draggable debug panel that breaks every frame down by pipeline stage — input, animation, layout,
draw, sync, command issue, buffer swap, GPU — and names the stage that is actually limiting your
frame rate.

The panel renders in its own window, so it never shows up in the metrics of the window it measures.

<img src="docs/panel.png" alt="The panel over the sample app, GPU bound while scrolling" width="420">

## Install

```kotlin
dependencies {
    debugImplementation("io.github.timkrest:framehud:0.1.0")
    releaseImplementation("io.github.timkrest:framehud-noop:0.1.0")
}
```

`framehud-noop` mirrors the API exactly with empty implementations, so your calls still compile in
release builds while nothing is measured and no window is added.

> **The `framehud` artifact declares `SYSTEM_ALERT_WINDOW`**, and manifest merging pulls it into any
> app that depends on it. That is why release builds should depend on `framehud-noop` — otherwise
> your users will see the permission.

Requires `minSdk` 24. Frame phases come from `FrameMetrics`; GPU timings need API 31+.

There is no code to write: the library installs itself in the main process and follows the resumed
activity.

## Configure

Settings live in one immutable config — assign a copy and it takes effect at once.

```kotlin
FrameHud.config = FrameHud.config.copy(
    enabled = false,                       // start hidden; show() flips it back
    overlayMode = OverlayMode.APP_WINDOW,  // never use SYSTEM_ALERT_WINDOW
    metricsSampleWindowSize = 240,
)
```

`show()`, `hide()` and `toggle()` are shortcuts for `enabled`. The panel is not the only way to read
the numbers: `FrameHud.metrics`, `memoryStats`, `thermalStats` and `vsyncRate` are plain `StateFlow`s.

## Jank events

Listeners are notified once per jank burst — not once per frame — with the cause already attributed:
thermal throttling, GC pauses, vsync starvation, late frame start, or the bottleneck stage. The
default listener writes to logcat.

```kotlin
FrameHud.config = FrameHud.config.copy(
    eventListeners = listOf(
        LogcatEventListener,
        FrameHudEventListener { event -> analytics.log(event.summary) },
    ),
)
```

Events arrive on the metrics thread — don't block, don't touch views.

## Fail tests on jank

`framehud-instrumentation` turns the panel into a CI gate:

```kotlin
androidTestImplementation("io.github.timkrest:framehud-instrumentation:0.1.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

The rule resets the collector before each test and asserts once the test passes — a failing test is
reported as-is. Annotate a test or class with `@SkipJankDetection` to opt out, or call
`JankAssertions.assertNoJank("scroll")` at a point you choose.

## Overlay permission

Without `SYSTEM_ALERT_WINDOW` the panel lives inside the app window and shows a `⧉` button that opens
the permission screen; the library never opens it on its own. A system overlay window keeps the panel
out of the metrics it measures, so granting the permission gives cleaner numbers.

To install by hand instead, drop the provider and call `FrameHud.install(application)`:

```xml
<provider
    android:name="io.github.timkrest.framehud.FrameHudInstaller"
    android:authorities="${applicationId}.framehud-installer"
    tools:node="remove" />
```

## Documentation

- [Reading the panel](docs/metrics.md) — what every row means, how to measure a screen, and what to
  do when something turns red
- [API reference](https://javadoc.io/doc/io.github.timkrest/framehud) — generated from the sources of
  each release
- [Roadmap](ROADMAP.md) — what is planned next, and what is deliberately not

## Sample

```
./gradlew :sample:installDebug
```

A 300-row list with five toggles — blocking the main thread, overdrawing, allocating per row, nesting
layouts, churning garbage — so you can watch each one move a different metric.

## Contributing

Pull requests are welcome — [CONTRIBUTING.md](CONTRIBUTING.md) covers how to build and what to check
before opening one. Contributions are covered by a [CLA](CLA.md), which a bot will ask you to sign on
your first pull request.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
