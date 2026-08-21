# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `FrameHudEvent.UsableFrame` reports when the app itself says the screen is ready, so a quickly
  drawn skeleton no longer counts as a ready screen. The measurement ends at the end of the next
  displayed frame. On the first screen of a `ComponentActivity` created while FrameHud collects,
  the `FullyDrawnReporter` reports on its own, so the `ReportDrawnWhen` already in place for
  Macrobenchmark is enough. Every other screen calls the new `FrameHud.reportUsable()`.
- `FrameHud.measureWindow` and `FrameHud.forgetWindow` measure a window FrameHUD cannot find on its
  own — a dialog, a popup, a `Presentation` on a second display — as a screen of its own, next to
  the activity in focus. Its frames count towards the session and towards that screen, so it reaches
  `screens()`, both reports and the baseline comparison; the live readings, marks and events stay
  with the activity the panel draws over.
- `FrameHud.processStats`, a `StateFlow<ProcessStats>` sampled every few seconds while a screen
  draws: CPU as a share of one core, PSS, thread count and open file descriptors, each with its peak
  since the last reset. The panel reads two rows from it, the session report writes it out, and
  every incident keeps the reading it was drawn under, so steady growth shows up next to the frames
  it eventually costs. Sampling runs on its own `framehud-process` thread, because reading PSS is
  slow enough on an older device to hold up frame collection.
- `FrameHud.screens()` answers with what the run measured per screen, worst first: most frozen
  frames, then most jank, then the slowest p95, with a sample too short to judge a p95 last. The
  panel shows the first eight of the same list when its header is tapped, and the HTML report has it
  as a `Screens` section. `IntervalId.name` reads the screen or mark an interval covers without
  matching on the type.
- `FrameHud.incidents()` answers with what each jank burst and frozen frame was drawn around: the
  frames on either side of the trigger, the stats those frames add up to, and the memory, thermal
  and process readings of that moment. Occurrences that blame the same thing on the same screen
  under the same mark and context are one `Incident` that counts them and keeps the worst
  `IncidentWindow`, so a report says layout jank happened seven times instead of showing seven
  copies of it.
  `FrameHudEvent.IncidentTrigger` names the events a window opens on. Both reports carry the
  incidents, worst case first, and the HTML report marks the trigger on each window's chart.
- `FrameHudEvent.InternalFailure` reports a failure FrameHUD caught in itself, with the call it
  failed in and the error, so an app can forward it to its own reporting instead of reading logcat.
  It arrives once per call until `reset`, and the stack reaches logcat every time it happens. A call
  that fails against a different version of a library the app ships is caught the same way, where it
  used to take the app down. What a listener of yours throws stays that listener's failure in the
  log, and never arrives as a failure of FrameHUD's.
- An incident keeps the stack of a main thread that was stuck within two seconds of the trigger. A
  main thread that goes 300 ms without handling a frame is sampled from a background thread every
  100 ms until it draws again, and `IncidentWindow.mainThreadBlock` says how long it was held, how
  many stacks were taken, and the calls those samples caught it in most often as `SampledCall`
  entries. The JSON has it as `mainThreadBlock`, and the HTML report lists it under the incident's
  chart.
- `FrameHud.counter` answers a `FrameHudCounter` that keeps a number the frame pipeline knows
  nothing about next to the frame data: an image decode queue, cache misses, items in flight. `set`
  replaces the value and `add` moves it, both from any thread, and the value is read on FrameHUD's
  own tick while every write raises the peak. The panel gets a row per counter and counts the rest
  past four, a system trace gets a `framehud:counter:<name>` track, the session report lists them
  all, and every incident keeps what they read when it fired. `FrameHud.counters` is the same list
  of `CounterReading` as a `StateFlow`, and sixteen names are tracked before the rest are dropped.
- `FrameHudConfig.frameBudgetsMs` gives a screen, a mark or the session a frame budget of its own,
  in place of the deadline the display hands out, so a scroll that has to hold 120 Hz no longer
  shares a threshold with a debug screen that never will. A budget covers everything its interval
  holds, and an entry deeper in wins: a screen follows the session, a mark follows its screen. Jank
  percent, lost time, the longest jank streak, the incidents an interval opens and the baseline it
  is compared against all follow the budget that judged its frames; `IntervalReport.frameBudgetMs`
  says which one that was, and the HTML report has it as a `Budget` column. The panel follows
  whichever budget is in force.
- `FrameWindowStats.frameBudgetMs` is the budget the rolling window was judged by. The panel reads
  it in place of `DisplayInfo.frameBudgetMs`, which stays what the display asked for.
- `FrameHud.diagnosis`, a `StateFlow<JankDiagnosis>` that says what the rolling window is bound by
  and what it blames. Reading it no longer means collecting four flows and calling `JankDiagnosis.of`
  yourself, which is why that factory is now internal. It reads healthy while no screen draws, and
  freezes with the other readings.
- `FrameHud.intervals()` answers with what the run measured per interval: the session, every screen
  and every mark, each with the frame budget that judged it, including the ones that drew no frame.
  `IntervalReport` was public and `Baseline.updatedWith` and `Baseline.compare` took it, but nothing
  handed one out, so a baseline of your own could only be built for the session and only without a
  budget.
- `FramePhases.get(FramePhase)` reads one phase of the last frames, the way `PhaseAverages.get`
  already read one phase of an interval.
- `BaselineEntry.of` takes the number of runs the stats already average.

### Changed

- A screen name and a mark name now follow the rule a counter name does: not blank, no longer than
  110 characters, and carrying no `|` or control character. A trace cuts a longer name down to one
  another name could share, and the rest end its record or split it, so two screens would have
  landed on one section. FrameHUD refuses such a name where the app sets it; over adb the command
  answers `failed:` and leaves the name alone.
- The export schema is 6: a `process` object holds the process health of the run and of each
  incident, and an `incidents` array holds the trigger with its diagnosis, how often the
  case happened, and the `worst` window with its stats, frames and readings. The `window` object
  carries the `frameBudgetMs` its numbers were judged by, and a `counters` array carries what the
  app kept, on the session and on each incident window alike. Each incident window also carries a
  `mainThreadBlock` object with the calls the main thread was sampled in.
- `framehud-metrics` depends on `androidx.activity`, which it reads the `FullyDrawnReporter` from.
- `SessionStats` is now `IntervalStats`, and the type that names it and carries its frame budget is
  now `IntervalReport`. Both types describe a session, a screen or a mark; only the old names said
  otherwise.
- Reading a session suspends instead of blocking. `awaitSessionStats`, `exportSession`,
  `saveBaseline` and `awaitBaselineComparison` are now `sessionStats()`, `exportSession()`,
  `saveBaseline()` and `compareWithBaseline()`, and none of them takes a timeout: wrap the call in
  `withTimeout` when a run must not wait. The export and the baseline write run on `Dispatchers.IO`
  on their own, so an app no longer has to move them off the main thread by hand. In a test without a
  coroutine, `runBlocking` does what the blocking calls did.
- `null` now says one thing per call. `sessionStats()` always answers, with `IntervalStats.EMPTY`
  when nothing was collected; `exportSession()` returns null when nothing was collecting;
  `saveBaseline()` returns null when the session recorded no frame; and a call that finds FrameHud
  uninstalled throws instead of logging and answering null.
- `compareWithBaseline()` never returns null: a device with no baseline yet is
  `BaselineComparison.NoBaseline`. The JSON export writes the same `"baseline": null` it always did.
- `IntervalComparison.interval` is now `id`, the name `IntervalReport` already uses.
- `BaselineEntry` no longer copies. Its `copy` carried the internal record of which runs measured a
  figure cleanly, so a changed number kept the trust of the number it replaced. Build an entry with
  `BaselineEntry.of`, which now takes `runs`.
- The sample now shows what this release added. Three tabs cover one run: a load screen that judges
  frames by a budget, marks its scrolls, names every screen it shows and keeps two counters of its
  own; a readouts screen that reads every flow FrameHUD exposes without the panel; and a session
  screen with the intervals and their budgets, the screens worst first, the incidents, the baseline,
  freezing, toggling collection, the report and a dialog window handed over to be measured. The
  device tests that check how a screen gets named now launch an activity of their own, so the sample
  is free to name its screens the way an app does.

## [0.10.0] - 2026-08-17

### Added

- Baselines. A run compares itself with what earlier runs measured on the same device and Android
  version, and does it per session, per screen and per mark. The baseline is a
  file: `adb shell am broadcast -a com.timkrest.framehud.BASELINE <package>` averages the session
  into `framehud/baseline.json` next to the exports, and pushing that file back before the next run
  makes both reports carry the delta and name the phase that grew the most. A baseline recorded on
  another device is not compared, and the report says where it came from. A figure a confidence
  issue taints neither enters the baseline nor compares against it, and every delta says how many
  runs stand behind it. Jank percent and lost time follow the frame budget that judged them: on a
  display that switches refresh rates they move and compare only between runs under the same budget,
  and three runs in a row under a new one restart them from the latest. Saving twice without a reset
  in between leaves the file alone, so a repeated command cannot weigh one run twice. In code the
  same runs through `FrameHud.baselineOverride`, `FrameHud.saveBaseline` and
  `FrameHud.awaitBaselineComparison`. A baseline of your own is built from `BaselineEntry.of` and
  `BaselineEnvironment.current()`.
- `BaselineMetric` names what a baseline compares: `P95_MS`, `LOST_TIME_MS_PER_FRAME`,
  `FROZEN_PERCENT` and the rest. Each is per frame or a share of frames, unlike `MeasuredMetric`,
  which keeps naming what a confidence issue taints and holds lost time as a total and frozen frames
  as a count. `BaselineThresholds.metrics` takes the new type, so a metric no baseline can hold no
  longer compiles.
- `JankThresholds.baseline` fails a test on growth over the baseline instead of over a fixed
  number, and `JankThresholds.baselineOnly()` leaves nothing but that comparison.
  `BaselineThresholds` checks p95, jank percent and lost time by default and allows 10%. Growth
  under half a millisecond or half a percentage point is noise, and for lost time, which counts per
  frame, the line is a tenth of a millisecond. With no baseline yet the check logs and passes, and a
  baseline from another device is inconclusive. A chosen metric the comparison had to leave out is
  inconclusive as well, and the message says whether this run measured it with a confidence issue or
  the two runs were judged under different frame budgets.
- The JSON export carries an `intervals` array with the stats of every screen and mark the session
  collected, and a `baseline` object with the deltas and with the metrics it could not compare. The
  session keeps its own object and now names the frame budget it was judged against. Schema 5.
- `FramePhase` names one pipeline phase, and `PhaseAverages` reads by it.

### Changed

- `JankThresholds` takes a fifth parameter. Kotlin code keeps compiling, but the four-argument
  constructor is gone from the binary, so test code compiled against 0.9 needs a rebuild. It also
  refuses NaN and negative limits at construction; before, they silently turned the check off.

### Fixed

- Stopping collection left the metrics engine frozen while the panel showed it running, and two
  threads toggling the freeze could disagree about which state won.

## [0.9.0] - 2026-08-15

### Added

- `SessionStats.lostTimeMs` sums how far the late frames ran past their deadline. Jank percent
  counts an 18 ms frame and a 300 ms frame as one bad frame each; lost time keeps the difference.
  The panel adds a line for it once it is above zero, both reports carry it per session and per
  screen, and a refresh-rate change taints it the same way it taints jank percent.
- `JankThresholds.maxLostTimeMs` fails a test once a screen loses more than the given time. It is
  infinite by default, so nothing changes until you set it.
- The HTML report shows the phase breakdown the panel shows, inside the frame window and next to its
  chart, and names the stage the frames were bound by. Until now the phases reached the JSON only,
  so a shared report said a screen was slow without saying where the time went.
- The HTML report lists a confidence issue the current screen has and the session does not, so a
  short screen no longer looks as trustworthy as the session around it.
- `SessionStats.phases` breaks a session, a screen or a mark down by pipeline phase, as
  `PhaseAverages`. Until now phases were only ever measured over the last
  `metricsSampleWindowFrames` frames, so a report could say a screen was slow without saying where
  its time went. Events carry them, both reports carry them, and the HTML report has a section
  comparing the session against the screen.

### Changed

- The export schema is 4: `session` and `screen` stats carry `lostTimeMs` and their own `phases`
  block, and the window's `phases` moved into `window`, since both describe the same last
  `metricsSampleWindowFrames` frames and not the session. `bottleneckStage` sits alongside them. A
  phase peak spans the whole session, not that window, so `peakMs` is now `peakSinceResetMs`.
- `FramePhases.gpu` and `PhaseAverages.gpu` are null until the driver reports GPU time, and
  `isGpuAvailable` is gone from both. A zero read as a real measurement before; now there is nothing
  to read. In the export, both `gpuMs` and the window's `gpu` are `null` on a device that never
  reported GPU time, and the `isGpuAvailable` field is gone.
- `FramePhases.other` and `PhaseAverages.other` are now `unattributed`.
- `FrameHudEvent.MarkEnded` takes `screen` before `mark`, the order the other events already use.
  Named construction is unaffected; positional construction silently swapped the two, since both
  are strings.
- `ScreenEnded`/`MarkEnded` summaries name lost time between jank percent and p95.
- `MemoryStats.gcCount` and `gcTimeMs` now count only while a screen is on top, matching the
  interval `SessionStats.durationMs` already measured.
- The `SessionStats` data class gained `lostTimeMs` after `jankPercent` and `phases` before
  `confidence`. Named construction and `copy` are unaffected; positional construction and code
  compiled against 0.8.0 need updating.

### Fixed

- An export taken after the app left the foreground named no screen while still reporting that
  screen's numbers. The last screen measured keeps its name until the next one binds.
- A screen named through `FrameHud.screen` stayed the active screen after its window was gone, so an
  event raised from the background carried it while a screen named after its activity carried
  nothing. Neither does now; `FrameHud.screen` still holds the name for the next window, as
  documented.
- Changing `metricsSampleWindowFrames` reset every peak, though a peak counts since the last
  `reset()` and not since the window it outlives. The window resizes now instead of being replaced.
- `MemoryStats.gcCount` and `gcTimeMs` counted collections the app ran in the background, while
  `SessionStats.durationMs` skips that time. `JankCause.Gc.timeShare` divides one by the other, so
  after a long background spell any jank was blamed on GC and the share could read above the 0..1 it
  documents. Both now cover the same interval.
- Enabling FrameHUD while the app sat in the background recorded a heap peak with no screen to
  attribute it to, because starting sampled the monitors before any window was bound. Monitors are
  now read only while a screen is on top, so `adb shell am broadcast … ENABLE` from the background
  no longer plants a peak.
- `ThermalStats.headroom` said values closer to `1.0` mean harder throttling. `1.0` is where severe
  throttling begins and anything above it is past that point; lighter throttling starts below it.
- `SessionStats` said its aggregates run since the last reset, which holds for a session and not for
  the screen and mark stats carried by `ScreenEnded` and `MarkEnded`.
- The panel put the pipeline row (`pipe:cpu`, `pipe:gpu`) one character past the label column, and
  any timing of a second or more past the value column, so the row a stall produced was the one that
  stopped lining up. Labels have room now, and a timing that does not fit drops its decimal.
- A broadcast from another app could crash a debuggable build: reading the extras of `SCREEN`,
  `MARK` or `CONTEXT` unpacks the whole bundle, and a bundle naming a class the app cannot load
  throws on the main thread. Extras that cannot be read are now logged and ignored.
- Two exports taken in the same millisecond wrote to the same two files, so the first caller was
  handed a path holding the second caller's report. Each export claims its own name.
- `JankThresholds.maxFrozenFrames` set to `Int.MAX_VALUE` still let a confidence issue on frozen
  frames make a run inconclusive, though the class documents a threshold no run can reach as off.
- `FrameHud.mark` accepted a blank name where `screen` and `context` reject one.
- A system overlay opened on the default display even when the app was running on another one, so
  the panel could sit on a screen nobody was looking at.

## [0.8.0] - 2026-08-15

### Added

- `SessionStats.confidence` lists what got in the way while the stats collected: dropped
  `FrameMetrics` reports, a slow event listener, thermal throttling, power save or low battery, a
  refresh-rate change, an emulator, a short sample. Each issue names the figures it taints.
  Exported reports carry the issues, and `ScreenEnded`/`MarkEnded` summaries end with
  `(suspect measurement)`.
- The jank gate has a third result. A checked threshold whose figure a confidence issue taints is
  inconclusive instead of passed or failed, and the message names both the figure and the issue.
  `OnInconclusive.FAIL` is the default and fails the test; `OnInconclusive.WARN` logs the message
  and passes. `assertNoJank` and `DetectJankAfterTestSuccess` take the new parameter.

### Changed

- The export schema is 2: `session` and `screen` stats carry a `confidence` block with the issues
  and the figures they affect.
- The `SessionStats` data class gained a `confidence` constructor parameter with a default, so
  sources compile as they are, but code compiled against 0.7.0 that constructs or `copy`-ies it
  needs a recompile.
- `assertNoJank` and the `DetectJankAfterTestSuccess` constructor gained an `onInconclusive`
  parameter with a default. Sources compile as they are and Java keeps the shorter overloads, but a
  test binary compiled against 0.7.0 needs a recompile.
- A gate run with fewer than 60 frames is inconclusive for jank percent and fails under the strict
  default. Render longer, or pass `OnInconclusive.WARN`.

### Removed

- `JankThresholds.maxDroppedReports`. Dropped reports now make the gate inconclusive; the strict
  default still fails CI. Delete the argument.

## [0.7.0] - 2026-08-13

### Added

- `FrameHud.screen` names the current screen with a route pattern like `product/{id}` instead of
  the activity class. A new name closes the stats of the previous screen and starts the next while
  the window stays bound, so a single-activity app is no longer one screen for the whole session.
- `FrameHud.context` keeps `key=value` measurement context — a UI variant, an action, a test
  scenario. Every `FrameHudEvent` carries the pairs set at the moment it fired.
- `FrameHud.exportSession` writes the session as JSON and a self-contained HTML report — stats, the
  frame window, the worst frames with wall-clock timestamps, context, device, API level, app
  version, refresh rate and measurement state — into `framehud/` under the app's external files
  directory. `FrameHud.shareSession` opens the system share sheet with both files. Nothing is
  uploaded anywhere.
- Broadcast commands `com.timkrest.framehud.ENABLE`, `DISABLE`, `RESET`, `SCREEN`, `MARK`,
  `CONTEXT` and `EXPORT` drive a debuggable build over `adb shell am broadcast`; `EXPORT` answers
  with the report's path. Builds that are not debuggable ignore them, and `framehud-noop` registers
  no receiver.
- A recorded system trace gains `framehud:screen:<name>` and `framehud:mark:<name>` sections, a
  `framehud:jank_burst` section, and `framehud.*` counters for jank percent, p95, frozen frames and
  heap use.

### Changed

- The `FrameHudEvent` data classes gained a `context` constructor parameter with a default, so
  sources compile as they are, but code compiled against 0.6.0 that constructs or `copy`-ies events
  needs a recompile.
- `FrameHudEvent.ScreenEnded` also fires when a screen is renamed through `FrameHud.screen`, not
  only when it pauses, is replaced or collection stops.
- `framehud-metrics` depends on `androidx.core` and `androidx.tracing` and declares a
  `FileProvider` limited to its own `framehud/` export directory.

## [0.6.0] - 2026-08-12

### Added

- `com.timkrest:framehud-metrics` collects without the panel: the same `FrameHud` API, the same
  events and session stats, no window and no `SYSTEM_ALERT_WINDOW` in the merged manifest.
- `FrameHudEvent.FirstFrame` reports how long each Activity instance took from before `onCreate` to
  its first displayed frame, on API 29 and newer. First draws remain excluded from jank and
  percentile statistics.

### Changed

- `FrameHud` ships in `framehud-metrics` now, and `framehud` holds the panel alone. Its package and
  all of its declared members are unchanged, and `framehud` depends on `framehud-metrics`, so
  `debugImplementation("com.timkrest:framehud:x.y.z")` needs no edit and code built against 0.5.0
  keeps finding the class. Only a build that takes the AAR without its transitive dependencies has
  to name `com.timkrest:framehud-metrics` itself.
- The collector is built without the Compose compiler, so `FrameHud` lost the `$stable` field that
  compiler generated. It was never a declared member, and `framehud-noop` never had one.
- `framehud-instrumentation` now depends on `framehud-metrics` instead of `framehud`, so a test APK
  that only asserts jank no longer pulls in Compose and `SYSTEM_ALERT_WINDOW`.
- A frozen panel stays frozen when the app goes to the background in `APP_WINDOW` mode. It already
  did with a system overlay.

### Fixed

- Collection no longer waits for the panel window. A window the platform refused to add left the
  session without frames for as long as it kept refusing.

## [0.5.0] - 2026-08-09

### Added

- `FrameHud.mark` attributes frames to an interaction instead of to whichever activity has focus.
  The header names it in place of the timing, every event carries it, and clearing it reports a
  `FrameHudEvent.MarkEnded` covering that stretch alone. Leaving the screen clears it.
- `JankThresholds.maxDroppedReports` — dropped `FrameMetrics` reports fail the gate. They
  undersample every other figure it checks, so a lossy run could pass on numbers nobody collected.
  Defaults to zero.

### Changed

- `FrameHud.vsyncRate` is now `choreographerTicksPerSecond`, and the matching
  `JankDiagnosis.of` parameter follows it. The old name looked like the display refresh rate; this
  value is the number of Choreographer ticks the main thread handled per second.
- `FrameHudEvent` gained `mark`, and `JankBurst`, `FrozenFrames` and `ThermalChanged` take it in the
  constructor. Only the library emits them, so this shows up in your code only if you build events
  by hand in tests.
- `FrameHudConfig.metricsSampleWindowSize` is now `metricsSampleWindowFrames`, with the default
  constant renamed to match. The unit belongs in the name, as it already is one line below.
- `JankCause.Stage.avgMs` is now `averageMs`, spelled like `MetricValue.average`.
- `FrameHud` carries `@MainThread`, `@AnyThread` and `@WorkerThread`, so lint reports a call made from
  the wrong thread instead of leaving it to the runtime check. `framehud-noop` mirrors them.
- `FrameHistory` carries the deadline each frame had beside its duration, so `get(index)` gave way
  to `totalMsAt(index)` and `deadlineMsAt(index)`. A frame can now be judged against the deadline it
  actually had rather than against the one in force at the time of reading.
- `FrameHudConfig` rejects a window of no frames, a negative throttle and a fallback refresh rate
  that is not finite and positive, instead of quietly clamping them once the collector got that far.
  The artifact is `debugImplementation` only, so the throw lands in the build that wrote the value.

### Fixed

- The refresh rate was read off the view hierarchy from the metrics thread every frame. `View.getDisplay`
  is UI-thread only; the `Display` is now taken when the window is bound and read from there.
- Events raised after leaving a screen still carried its name instead of null.
- The `over` row stayed green until a frame lost a whole extra budget. It was coloured as a frame
  time rather than as the overrun it holds.
- The panel could land off-screen after a rotation, with no way to drag it back. Its position was
  clamped to the display only while dragging, never when the display changed underneath it.
- The collapsed sparkline covered half a second where the figures printed beside it covered the whole
  window. It dropped the frames it had no room for; now each bar keeps the worst frame of its slice,
  so both span the same window and a spike survives however narrow the chart is.
- One slow frame flattened the sparkline for as long as it stayed in the window, and moved the budget
  line with it. The scale now climbs in doublings of the budget and stops at sixteen.
- Sparkline bars stretched to fill the width and narrowed as the window filled. They now keep a slot
  width and fill from the right.

## [0.4.0] - 2026-08-02

### Added

- `SessionStats.FROZEN_FRAME_MS` — the threshold Play Vitals counts as a frozen frame, so a caller
  reading `frozenFrames` can name the same number the library uses.

### Fixed

- Dragging the panel slowly moved it nowhere. Each pointer event was rounded to whole pixels on its
  own, so on a high-polling-rate screen every step rounded to zero and the fraction was lost.

## [0.3.0] - 2026-08-01

### Changed

- The Maven coordinates are `com.timkrest` and the packages are `com.timkrest.framehud`, replacing
  `io.github.timkrest` in both. Update the dependency lines and the imports; a manifest override that
  names the installer takes `com.timkrest.framehud.FrameHudInstaller`. 0.1.0 and 0.2.0 stay published
  under the old coordinates and are not republished under the new ones.
- `framehud-noop` left the documented install. `debugImplementation` already keeps the artifact, its
  provider and the `SYSTEM_ALERT_WINDOW` it declares out of a release build, so the no-op is needed
  only where app code references `FrameHud` outside `src/debug`.

### Fixed

- Split-screen on Android 10 and up resumes every visible activity, so the panel could measure a
  window other than the one being used. It follows the focused window instead.

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

[Unreleased]: https://github.com/timkrest/FrameHUD/compare/v0.10.0...HEAD
[0.10.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.10.0
[0.9.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.9.0
[0.8.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.8.0
[0.7.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.7.0
[0.6.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.6.0
[0.5.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.5.0
[0.4.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.4.0
[0.3.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.3.0
[0.2.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.2.0
[0.1.0]: https://github.com/timkrest/FrameHUD/releases/tag/v0.1.0
