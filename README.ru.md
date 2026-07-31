# FrameHUD

[![Maven Central](https://img.shields.io/maven-central/v/io.github.timkrest/framehud)](https://central.sonatype.com/artifact/io.github.timkrest/framehud)
[![Build](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml/badge.svg)](https://github.com/timkrest/FrameHUD/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

[English](README.md) · [Русский](README.ru.md)

Перетаскиваемая debug-панель, которая раскладывает каждый кадр по стадиям конвейера и показывает, на
какой из них упирается fps.

<img src="docs/panel.png" alt="Панель поверх sample-приложения во время прокрутки списка на 120 Гц" width="420">

У каждой стадии своя строка: `input`, `anim`, `layout` и `draw` на main thread, дальше `sync`,
`command` и `swap` на render thread, над ними `gpu`. В каждой строке текущий кадр и среднее по окну,
а у измеряемых фаз ещё и пик с последнего сброса.

Панель не просто печатает числа. Она называет стадию, в которую упёрся fps, а когда кадры теряются,
объясняет причину: троттлинг, паузы GC, нехватка vsync-тиков или поздний старт кадра. Панель получает
собственное окно, поэтому в метрики измеряемого окна не попадает.

## Подключение

```kotlin
dependencies {
    debugImplementation("io.github.timkrest:framehud:0.2.0")
    releaseImplementation("io.github.timkrest:framehud-noop:0.2.0")
}
```

Требуется `minSdk` 24. Фазы кадра берутся из `FrameMetrics`, тайминги GPU — с API 31+ и только если
драйвер их сообщает.

Вызывать ничего не нужно. `ContentProvider` поднимает панель в главном процессе, дальше она следует
за той activity, которая сейчас на экране.

В релизных сборках оставляйте `framehud-noop`. Он повторяет API с пустыми реализациями: вызовы
компилируются, но ничего не измеряется и окно не добавляется. Заодно он не пускает
`SYSTEM_ALERT_WINDOW` в APK — артефакт `framehud` объявляет это разрешение, и manifest merging тянет
его в любое приложение, которое от него зависит.

На эмуляторе render thread и GPU принадлежат хост-машине, поэтому эти строки описывают ваш десктоп,
а не устройство. Панель ставит в шапке `EMU`, помечает эти секции `· host` и гасит их строки. Фазы
main thread, jank и итоги сессии остаются осмысленными — именно их читает jank-гейт на CI.

## Настройка

Все настройки — один неизменяемый конфиг. Присваиваете копию, она применяется сразу.

```kotlin
FrameHud.config = FrameHud.config.copy(metricsSampleWindowSize = 240)
```

| Настройка | По умолчанию | Что задаёт |
| --- | --- | --- |
| `enabled` | `true` | Пока false — окно не добавляется и кадры не собираются. |
| `overlayMode` | `PREFER_SYSTEM` | `APP_WINDOW` держит панель внутри окна приложения и не использует разрешение. |
| `eventListeners` | `[LogcatEventListener]` | Кто получает события о jank, замёрзших кадрах, троттлинге и итогах экрана. |
| `metricsSampleWindowSize` | `120` | Сколько последних кадров хранит окно — за ним стоят `avg` и перцентили. |
| `metricsThrottleIntervalMs` | `400` | Как часто панель может перерисовываться. Меньше — дороже рендер. |
| `fallbackRefreshRateHz` | `60` | Герцовка, если дисплей её не сообщает. |
| `metricsThreadName` | `framehud-metrics` | Имя потока сбора — под ним он виден в трейсах. |

`show()`, `hide()` и `toggle()` — сокращения для `enabled`. Панель не единственный способ читать
метрики: `FrameHud.metrics`, `memoryStats`, `thermalStats` и `vsyncRate` — обычные `StateFlow`.
Показание разложено на `phases` (тайминги по стадиям), `window` (fps, jank и p95 по окну выборки),
`session` (с последнего сброса) и `display` (герцовка и бюджет кадра).

## События о jank

Слушателей уведомляют один раз на всплеск jank, а не на каждый кадр, и причина к этому моменту уже
определена. Слушатель по умолчанию пишет в logcat.

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
androidTestImplementation("io.github.timkrest:framehud-instrumentation:0.2.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

Правило сбрасывает сборщик перед каждым тестом и проверяет пороги после того, как тест прошёл, и
упавший тест остаётся с собственной ошибкой. Итоги сессии переживают закрытие панели, поэтому
проверке есть что читать и после того, как `ActivityScenario` закрыл activity. Чтобы отключить
проверку, пометьте тест или класс
`@SkipJankDetection` либо вызовите `JankAssertions.assertNoJank("scroll")` в нужный момент сами.

## Разрешение на оверлей

С выданным `SYSTEM_ALERT_WINDOW` панель живёт в системном окне и переживает переходы между экранами.
Без него окно принадлежит текущей activity, поэтому на каждом переходе панель закрывается и
поднимается заново, а рядом появляется кнопка `⧉`, открывающая экран разрешения. Сама библиотека его
не открывает. Поставьте `overlayMode = APP_WINDOW`, чтобы остаться в окне приложения и убрать эту
кнопку.

<details>
<summary>Установка вручную вместо provider'а</summary>

Уберите provider и вызовите `FrameHud.install(application)` сами:

```xml
<provider
    android:name="io.github.timkrest.framehud.FrameHudInstaller"
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
- [Справочник API](https://javadoc.io/doc/io.github.timkrest/framehud) — генерируется из исходников
  каждого релиза
- [Карта развития](ROADMAP.ru.md) — что планируется дальше и что сделано не будет
- [Как участвовать](CONTRIBUTING.md) — как собрать проект и что проверить перед pull request. Вклад
  покрывается [CLA](CLA.md) — бот попросит его подписать.

## Лицензия

Apache 2.0. См. [LICENSE](LICENSE) и [NOTICE](NOTICE).
