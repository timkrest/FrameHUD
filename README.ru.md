# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/com.timkrest/framehud)](https://central.sonatype.com/artifact/com.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![API 24+](https://img.shields.io/badge/API-24%2B-brightgreen)](https://developer.android.com/tools/releases/platforms)

[English](README.md) · [Русский](README.ru.md)

Перетаскиваемая debug-панель: показывает, сколько занимает каждая стадия кадра и какая из них вас
тормозит.

<img src="docs/panel.png" alt="Панель поверх примера при скролле списка на 120 Гц" width="420">

- **Строка на каждую стадию**. `input`, `anim`, `layout`, `draw` на main thread, затем `sync`,
  `command`, `swap` на render thread, и `gpu`
- **Говорит, почему кадры теряются**. Троттлинг, паузы GC, редкие тики Choreographer, поздний старт
- **Хранит сам случай, а не только цифру**. Всплеск jank или замороженный кадр сохраняется вместе с
  кадрами вокруг него и показаниями на тот момент, а если вставал главный поток — то и со стеком, в
  котором он стоял
- **Меряет приложение, а не себя**. Панель рисуется в своём окне
- **Роняет тесты из-за jank**. JUnit-правило с порогами
- **Сравнивает с прошлыми прогонами**. Базлайн на каждое устройство, и гейт проверяет дельту, а не
  фиксированное число
- **Работает и без панели**. `framehud-metrics` собирает и отчитывается без окна и без разрешения
- **Сделан для QA-прогонов**. Команды adb, экспорт сессии в JSON и HTML, секции и счётчики в
  системном трейсе
- **Ничего в релизных сборках**. `debugImplementation` оставляет за бортом панель, её provider и
  `SYSTEM_ALERT_WINDOW`, который объявляет артефакт

## Быстрый старт

```kotlin
dependencies {
    debugImplementation("com.timkrest:framehud:0.16.0")
}
```

Вызывать ничего не нужно. `ContentProvider` поднимает панель, а дальше она следует за activity,
у которой фокус.

Нужен `minSdk` 24. Фазы кадра приходят из `FrameMetrics`; тайминги GPU требуют API 31+ и драйвера,
который их сообщает.

`debugImplementation` уже не пускает ничего в релиз. `framehud-noop` нужен, только если вы вызываете
`FrameHud` вне `src/debug`: релизной сборке всё равно надо скомпилировать эти строки. Он повторяет
API с пустыми телами:

```kotlin
releaseImplementation("com.timkrest:framehud-noop:0.16.0")
```

## Что показывает панель

```
ui 118/s · 8.3ms  118 FPS
⚠ layout 8.4 ms
CPU        now   avg  peak
input      0.1   0.2   1.1
anim       0.3   0.4   2.0
layout     7.9   8.4  22.3 ◀
draw       1.2   1.4   6.7
RENDER
sync       0.4   0.5   1.9
command    0.6   0.7   3.1
swap       0.2   0.3   1.4
GPU
gpu        2.1   2.4   9.8
delay      0.3   0.4   2.2
other      0.1   0.2
TOTAL     13.2  14.5  38.6
over       4.9   6.2  30.3
pipe:cpu  10.4  11.0
win  jank  4.2%  p95  12.1  max  22.3
ses  p50   7.1  p95  12.4  p99  19.8
ses 4312f 1m12s jank 4.2% frz0 run3
lost 2.1s
mem 84/256 ▲96 · nat 37 ▲41 MB
gc x3 · 18 ms
therm none · hr 0.68
cpu 62% ▲140 · pss 214 ▲240 MB
thr 38 ▲44 · fd 210 ▲260
```

Шапка показывает частоту тиков Choreographer на main thread, бюджет кадра и FPS. Вердикт под ней
называет строку, на которую стоит смотреть, а `◀` её помечает. Колонки читаются как `now avg peak`:
текущий кадр, среднее по окну и пик с последнего сброса. Нажатие на шапку переключает панель на
[худшие экраны](docs/guide.ru.md#история-экранов) и обратно, а удержание замораживает показания.

[Как читать панель](docs/metrics.ru.md) объясняет каждую строку и что делать, когда она краснеет.

## Падение тестов из-за jank

```kotlin
androidTestImplementation("com.timkrest:framehud-instrumentation:0.16.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

Пороги бывают фиксированными, как выше, или
[относительными](docs/guide.ru.md#падение-тестов-из-за-jank): от базлайна прошлых прогонов на этом
же устройстве.

## Пример

```
./gradlew :sample:installDebug
```

**Load** нагружает конвейер кадра шестью переключателями, **Readouts** показывает каждое показание,
прочитанное из флоу, а не с панели, **Session** — то, чем заканчивается прогон QA: базлайн, прошлые
прогоны, инциденты, экспорт.

## Документация

- [Руководство](docs/guide.ru.md): сбор без панели, настройка, события и инциденты, flight recorder
  Perfetto, экраны и метки, экспорт и adb, базлайны и jank-гейт
- [Как читать панель](docs/metrics.ru.md): что означает каждая строка, как замерять экран и что
  делать, когда что-то красное
- [Сравнение инструментов](docs/comparison.ru.md): чем FrameHUD отличается от JankStats,
  Macrobenchmark, Perfetto и Play Vitals
- [Справочник API](https://javadoc.io/doc/com.timkrest/framehud-metrics): генерируется из исходников
  каждого релиза
- [Список изменений](CHANGELOG.md): что поменялось в каждом релизе
- [Как участвовать](CONTRIBUTING.md): как собрать проект и что проверить перед pull request. Вклад
  покрывается [CLA](CLA.md), бот попросит его подписать.

## Лицензия

Apache 2.0. См. [LICENSE](LICENSE) и [NOTICE](NOTICE).
