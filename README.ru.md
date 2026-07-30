# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/io.github.timkrest/framehud)](https://central.sonatype.com/artifact/io.github.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

[English](README.md) · [Русский](README.ru.md)

Перетаскиваемая debug-панель, которая раскладывает каждый кадр по стадиям конвейера — input,
animation, layout, draw, sync, command issue, swap, GPU — и называет стадию, которая на самом деле
ограничивает fps.

Панель рисуется в отдельном окне и в метрики измеряемого окна не попадает.

<img src="docs/panel.png" alt="Панель поверх sample-приложения, узкое место — GPU при прокрутке" width="420">

## Подключение

```kotlin
dependencies {
    debugImplementation("io.github.timkrest:framehud:0.1.0")
    releaseImplementation("io.github.timkrest:framehud-noop:0.1.0")
}
```

`framehud-noop` повторяет публичный API один в один с пустыми реализациями: вызовы компилируются в
релизной сборке, но ничего не измеряется и окно не добавляется.

> **Артефакт `framehud` объявляет `SYSTEM_ALERT_WINDOW`**, и manifest merging тянет разрешение в
> приложение. Поэтому в релизе нужен `framehud-noop` — иначе разрешение увидят пользователи.

Требуется `minSdk` 24. Фазы кадра берутся из `FrameMetrics`, тайминги GPU — с API 31+.

Писать код не нужно: библиотека сама устанавливается в главном процессе и следует за активной
activity.

## Настройка

Все настройки — один неизменяемый конфиг: присваиваете копию, она применяется сразу.

```kotlin
FrameHud.config = FrameHud.config.copy(
    enabled = false,                       // стартовать скрытой; show() включит обратно
    overlayMode = OverlayMode.APP_WINDOW,  // никогда не использовать SYSTEM_ALERT_WINDOW
    metricsSampleWindowSize = 240,
)
```

`show()`, `hide()` и `toggle()` — сокращения для `enabled`. Панель не единственный способ читать
метрики: `FrameHud.metrics`, `memoryStats`, `thermalStats` и `vsyncRate` — обычные `StateFlow`.

## События о jank

Слушатели получают уведомление один раз на всплеск jank — не на каждый кадр — с уже определённой
причиной: троттлинг, паузы GC, нехватка vsync-тиков, поздний старт кадра или узкая стадия конвейера.
Слушатель по умолчанию пишет в logcat.

```kotlin
FrameHud.config = FrameHud.config.copy(
    eventListeners = listOf(
        LogcatEventListener,
        FrameHudEventListener { event -> analytics.log(event.summary) },
    ),
)
```

События приходят на metrics-потоке — не блокируйте его и не трогайте из него view.

## Падение тестов из-за jank

`framehud-instrumentation` превращает панель в гейт для CI:

```kotlin
androidTestImplementation("io.github.timkrest:framehud-instrumentation:0.1.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

Правило сбрасывает сборщик перед каждым тестом и проверяет пороги после того, как тест прошёл —
упавший тест остаётся с собственной ошибкой. Чтобы отключить проверку, пометьте тест или класс
`@SkipJankDetection`, либо вызывайте `JankAssertions.assertNoJank("scroll")` в нужный момент сами.

## Разрешение на оверлей

Без `SYSTEM_ALERT_WINDOW` панель живёт внутри окна приложения и показывает кнопку `⧉`, которая
открывает экран разрешения; сама библиотека его не открывает. Системное окно оверлея не попадает в
метрики измеряемого окна, поэтому с разрешением числа чище.

Чтобы устанавливать вручную, уберите provider и вызовите `FrameHud.install(application)`:

```xml
<provider
    android:name="io.github.timkrest.framehud.FrameHudInstaller"
    android:authorities="${applicationId}.framehud-installer"
    tools:node="remove" />
```

## Документация

- [Как читать панель](docs/metrics.ru.md) — что означает каждая строка, как замерять экран и что
  делать, когда что-то красное
- [Справочник API](https://javadoc.io/doc/io.github.timkrest/framehud) — генерируется из исходников
  каждого релиза
- [Карта развития](ROADMAP.ru.md) — что планируется дальше и что сделано не будет

## Пример

```
./gradlew :sample:installDebug
```

Список из 300 строк и пять переключателей — блокировка main thread, overdraw, аллокации на строку,
вложенные layout'ы, поток мусора — чтобы посмотреть, как каждый двигает свою метрику.

## Вклад в проект

Pull request'ы приветствуются — в [CONTRIBUTING.md](CONTRIBUTING.md) описано, как собрать проект и
что проверить перед отправкой. Вклад покрывается [CLA](CLA.md) — бот попросит подписать его при
первом pull request.

## Лицензия

Apache 2.0. См. [LICENSE](LICENSE) и [NOTICE](NOTICE).
