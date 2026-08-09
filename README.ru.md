# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/com.timkrest/framehud)](https://central.sonatype.com/artifact/com.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![API 24+](https://img.shields.io/badge/API-24%2B-brightgreen)](https://developer.android.com/tools/releases/platforms)

[English](README.md) · [Русский](README.ru.md)

Перетаскиваемая debug-панель: показывает, сколько занимает каждая стадия кадра и какая из них вас
тормозит.

<img src="docs/panel.png" alt="Панель поверх примера при скролле списка на 120 Гц" width="420">

- **Строка на каждую стадию** — `input`, `anim`, `layout`, `draw` на main thread, затем `sync`,
  `command`, `swap` на render thread, и `gpu`
- **Говорит, почему кадры теряются** — троттлинг, паузы GC, пропущенные тики vsync, поздний старт
- **Меряет приложение, а не себя** — панель рисуется в своём окне
- **Роняет тесты из-за jank** — JUnit-правило с порогами
- **Ничего в релизных сборках** — `debugImplementation` оставляет за бортом панель, её provider и
  `SYSTEM_ALERT_WINDOW`, который объявляет артефакт

## Быстрый старт

```kotlin
dependencies {
    debugImplementation("com.timkrest:framehud:0.4.0")
}
```

Вызывать ничего не нужно. `ContentProvider` поднимает панель, а дальше она следует за activity,
у которой фокус.

Нужен `minSdk` 24. Фазы кадра приходят из `FrameMetrics`; тайминги GPU требуют API 31+ и драйвера,
который их сообщает.

## Что показывает панель

```
ui 118/s · 8.3ms  118 FPS
⚠ layout 8.4 ms
CPU       now   avg  peak
input     0.1   0.2   1.1
anim      0.3   0.4   2.0
layout    7.9   8.4  22.3 ◀
draw      1.2   1.4   6.7
RENDER
sync      0.4   0.5   1.9
command   0.6   0.7   3.1
swap      0.2   0.3   1.4
GPU
gpu       2.1   2.4   9.8
delay     0.3   0.4   2.2
other     0.1   0.2
TOTAL    13.2  14.5  38.6
over      4.9   6.2  30.3
pipe:cpu 10.4  11.0
win  jank  4.2%  p95  12.1  max  22.3
ses  p50   7.1  p95  12.4  p99  19.8
ses 4312f 1m12s jank 4.2% frz0 run3
mem 84/256 ▲96 · nat 37 ▲41 MB
gc x3 · 18 ms
therm none · hr 0.68
```

В шапке — частота vsync, бюджет кадра и FPS. Вердикт под ней называет строку, на которую стоит
смотреть, а `◀` её помечает.

Колонки читаются как `now avg peak`: текущий кадр, среднее по окну и пик с последнего сброса.
Строки, собранные из других строк, заканчиваются на `avg`.

`over` — насколько кадр вышел за бюджет. `pipe:` — самая медленная стадия. `win` — про окно,
`ses` — про сессию.

[Как читать панель](docs/metrics.ru.md) объясняет каждую строку и что делать, когда она краснеет.

## Релизные сборки

`debugImplementation` уже не пускает ничего в релиз. `framehud-noop` нужен, только если вы вызываете
`FrameHud` вне `src/debug` — релизной сборке всё равно надо скомпилировать эти строки:

```kotlin
releaseImplementation("com.timkrest:framehud-noop:0.4.0")
```

Он повторяет API с пустыми телами. Вызовы компилируются, ничего не измеряется, окно не добавляется.

## Настройка

Все настройки — один неизменяемый конфиг. Присваиваете копию, она применяется сразу.

```kotlin
FrameHud.config = FrameHud.config.copy(metricsSampleWindowFrames = 240)
```

| Настройка | По умолчанию | Что задаёт |
| --- | --- | --- |
| `enabled` | `true` | Пока false — окно не добавляется и кадры не собираются. |
| `overlayMode` | `PREFER_SYSTEM` | `APP_WINDOW` держит панель внутри окна приложения и не использует разрешение. |
| `eventListeners` | `[LogcatEventListener]` | Кто получает события о jank, замёрзших кадрах, троттлинге и итогах экрана. |
| `metricsSampleWindowFrames` | `120` | За сколько кадров считаются `avg` и перцентили. |
| `metricsThrottleIntervalMs` | `400` | Как часто панель может перерисовываться. Меньше — дороже рендер. |
| `fallbackRefreshRateHz` | `60` | Герцовка, если дисплей её не сообщает. |
| `metricsThreadName` | `framehud-metrics` | Имя потока сбора — под ним он виден в трейсах. |

`show()`, `hide()` и `toggle()` — сокращения для `enabled`.

Панель не единственный способ читать метрики. `FrameHud.metrics`, `memoryStats`, `thermalStats` и
`vsyncRate` — обычные `StateFlow`. Показание разложено на `phases` (тайминги по стадиям), `window`
(fps, jank и p95 по окну выборки), `session` (с последнего сброса) и `display` (герцовка и бюджет
кадра).

## События о jank

Приходит одно событие на всплеск jank, а не на каждый кадр, и причина в нём уже определена.
Слушатель по умолчанию пишет в logcat.

```kotlin
FrameHud.config = FrameHud.config.copy(
    eventListeners = listOf(
        LogcatEventListener,
        FrameHudEventListener { event -> analytics.log(event.summary) },
    ),
)
```

События приходят на metrics-потоке. Не блокируйте его и не обращайтесь из него к view.

## Падение тестов из-за jank

```kotlin
androidTestImplementation("com.timkrest:framehud-instrumentation:0.4.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

Правило сбрасывает сборщик перед каждым тестом и проверяет пороги после того, как тест прошёл — так
упавший тест остаётся со своей ошибкой. Итоги сессии переживают закрытие панели, поэтому цифры есть
и после того, как `ActivityScenario` закрыл activity.

Чтобы отключить проверку, пометьте тест или класс `@SkipJankDetection` либо вызовите
`JankAssertions.assertNoJank("scroll")` в нужный момент сами.

## Разрешение на оверлей

С выданным `SYSTEM_ALERT_WINDOW` панель живёт в системном окне и переживает переходы между экранами.
Без него окно принадлежит текущей activity, поэтому на каждом переходе панель поднимается заново, а
рядом появляется кнопка `⧉`, открывающая экран разрешения.

Сама библиотека его не открывает. Поставьте `overlayMode = APP_WINDOW`, чтобы остаться в окне
приложения и убрать эту кнопку.

<details>
<summary>На эмуляторе</summary>

Render thread и GPU принадлежат хост-машине, поэтому эти строки описывают ваш десктоп, а не
устройство. Панель ставит в шапке `EMU`, помечает эти секции `· host` и гасит их строки.

Фазы main thread, jank и итоги сессии остаются осмысленными. Именно их читает jank-гейт на CI.

</details>

<details>
<summary>Установка вручную вместо provider'а</summary>

Уберите provider и вызовите `FrameHud.install(application)` из `Application.onCreate()`. Панель
появляется на следующей activity, которая выйдет на экран, поэтому вызов позже пропустит уже
открытый экран.

```xml
<provider
    android:name="com.timkrest.framehud.FrameHudInstaller"
    android:authorities="${applicationId}.framehud-installer"
    tools:node="remove" />
```

</details>

## Пример

```
./gradlew :sample:installDebug
```

Список из 300 строк и пять переключателей: блокировка main thread, overdraw, аллокации на строку,
вложенные layout'ы, поток мусора. Каждый двигает свою метрику.

## Документация

- [Как читать панель](docs/metrics.ru.md) — что означает каждая строка, как замерять экран и что
  делать, когда что-то красное
- [Справочник API](https://javadoc.io/doc/com.timkrest/framehud) — генерируется из исходников
  каждого релиза
- [Карта развития](ROADMAP.ru.md) — что планируется дальше и что сделано не будет
- [Список изменений](CHANGELOG.md) — что поменялось в каждом релизе
- [Как участвовать](CONTRIBUTING.md) — как собрать проект и что проверить перед pull request. Вклад
  покрывается [CLA](CLA.md) — бот попросит его подписать.

## Лицензия

Apache 2.0. См. [LICENSE](LICENSE) и [NOTICE](NOTICE).
