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
- **Говорит, почему кадры теряются** — троттлинг, паузы GC, редкие тики Choreographer, поздний старт
- **Меряет приложение, а не себя** — панель рисуется в своём окне
- **Роняет тесты из-за jank** — JUnit-правило с порогами
- **Работает и без панели** — `framehud-metrics` собирает и отчитывается без окна и без разрешения
- **Сделан для QA-прогонов** — команды adb, экспорт сессии в JSON и HTML, секции и счётчики в
  системном трейсе
- **Ничего в релизных сборках** — `debugImplementation` оставляет за бортом панель, её provider и
  `SYSTEM_ALERT_WINDOW`, который объявляет артефакт

## Быстрый старт

```kotlin
dependencies {
    debugImplementation("com.timkrest:framehud:0.8.0")
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
lost 2.1s
mem 84/256 ▲96 · nat 37 ▲41 MB
gc x3 · 18 ms
therm none · hr 0.68
```

В шапке — частота тиков Choreographer на main thread, бюджет кадра и FPS. Вердикт под ней называет
строку, на которую стоит смотреть, а `◀` её помечает.

Колонки читаются как `now avg peak`: текущий кадр, среднее по окну и пик с последнего сброса.
Строки, собранные из других строк, заканчиваются на `avg`.

`over` — насколько кадр вышел за бюджет. `pipe:` — самая медленная стадия. `win` — про окно,
`ses` — про сессию.

[Как читать панель](docs/metrics.ru.md) объясняет каждую строку и что делать, когда она краснеет.

## Релизные сборки

`debugImplementation` уже не пускает ничего в релиз. `framehud-noop` нужен, только если вы вызываете
`FrameHud` вне `src/debug` — релизной сборке всё равно надо скомпилировать эти строки:

```kotlin
releaseImplementation("com.timkrest:framehud-noop:0.8.0")
```

Он повторяет API с пустыми телами. Вызовы компилируются, ничего не измеряется, окно не добавляется.

## Сбор без панели

Сборка, которая должна отчитываться, но не рисовать, берёт `framehud-metrics` вместо `framehud`. Он
собирает те же числа и шлёт те же события, но не добавляет ни окна, ни `SYSTEM_ALERT_WINDOW` в
объединённый манифест.

```kotlin
qaImplementation("com.timkrest:framehud-metrics:0.8.0")
```

`FrameHud` — тот же объект, так что код вокруг остаётся прежним. `enabled` теперь включает только
сбор, `show()`, `hide()` и `toggle()` по-прежнему означают `enabled`, а `overlayMode` не используется.
`framehud` — это тот же артефакт плюс панель, так что debug-сборке больше ничего не нужно.

## Настройка

Все настройки — один неизменяемый конфиг. Присваиваете копию, она применяется сразу.

```kotlin
FrameHud.config = FrameHud.config.copy(metricsSampleWindowFrames = 240)
```

| Настройка | По умолчанию | Что задаёт |
| --- | --- | --- |
| `enabled` | `true` | Пока false — окно не добавляется и кадры не собираются. |
| `overlayMode` | `PREFER_SYSTEM` | `APP_WINDOW` держит панель внутри окна приложения и не использует разрешение. |
| `eventListeners` | `[LogcatEventListener]` | Кто получает события о первом кадре, jank, замёрзших кадрах, троттлинге, итогах экрана и взаимодействия. |
| `metricsSampleWindowFrames` | `120` | За сколько кадров считаются `avg` и перцентили. |
| `metricsThrottleIntervalMs` | `400` | Как часто панель может перерисовываться. Меньше — дороже рендер. |
| `fallbackRefreshRateHz` | `60` | Герцовка, если дисплей её не сообщает. |
| `metricsThreadName` | `framehud-metrics` | Имя потока сбора — под ним он виден в трейсах. |

`show()`, `hide()` и `toggle()` — сокращения для `enabled`.

Панель не единственный способ читать метрики. `FrameHud.metrics`, `memoryStats`, `thermalStats` и
`choreographerTicksPerSecond` — обычные `StateFlow`. Показание разложено на `phases` (тайминги по
стадиям), `window` (fps, jank и p95 по окну выборки), `session` (с последнего сброса) и `display`
(герцовка и бюджет кадра).

## Первый кадр

`FrameHudEvent.FirstFrame` сообщает, сколько экземпляр Activity шёл до первого показанного кадра.
Отсчёт начинается до `onCreate` и заканчивается в конце этого кадра. Слушатель по умолчанию пишет в
logcat: `MainActivity: first frame in 123.4 ms`.

Android считает первые кадры ожидаемо медленными, поэтому FrameHUD сообщает их отдельно и не
добавляет в оконную и сессионную статистику jank. Пересоздание Activity — новый замер, возврат в уже
существующую Activity — нет.

Событию нужен API 29 или новее: стартовая отметка берётся из `onActivityPreCreated`, который появился
в API 29. На старых версиях первый кадр по-прежнему исключается из статистики jank, но событие
`FirstFrame` не приходит.

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

## Имена экранов

Статистика режется по экранам, а экран по умолчанию — это класс activity, из-за чего
single-activity-приложение оказывается одним экраном на всю сессию. Назовите экран роутом:

```kotlin
navController.addOnDestinationChangedListener { _, destination, _ ->
    FrameHud.screen = destination.route
}
```

Используйте шаблон — `product/{id}`, а не `product/12345`, — чтобы каждая карточка товара считалась
одним экраном. Новое имя закрывает статистику предыдущего экрана и начинает следующий; окно остаётся
привязанным. Имя действует до следующего присваивания, поэтому приложение, которое называет экраны,
должно называть каждый показанный экран. `null` возвращает имена по классам activity.

## Метка взаимодействия

Итог по экрану говорит, какой экран медленный. Метка — какое взаимодействие.

```kotlin
FrameHud.mark = "scroll"
// идёт жест
FrameHud.mark = null
```

Кадры, отрисованные при выставленной метке, относятся к ней. В шапке вместо таймингов появляется
`▸ scroll`, каждое событие за это время несёт имя метки, а её сброс даёт `MarkEnded` со статистикой
только по этому отрезку. Строки самой панели остаются на обычном окне и сессии — шапка их
подписывает, а не сужает.

Уход с экрана или его переименование сбрасывает метку сам, так что жест не перетекает на следующий
экран.

## Контекст измерений

`FrameHud.context` хранит несколько пар `key=value` рядом с экраном и меткой — вариант UI, действие,
тестовый сценарий. Каждое событие несёт пары, выставленные в момент, когда оно случилось, и экспорт
их сохраняет — отчёт говорит не только где был jank, но и при каких условиях.

```kotlin
FrameHud.context = mapOf("variant" to "new_checkout")
```

Смена контекста ничего не закрывает — только подписывает то, что происходит дальше.

## Экспорт сессии

`exportSession` записывает сессию с последнего сброса — статистику, окно кадров, худшие кадры с
временем по часам, контекст, устройство, версию приложения, состояние измерений и проблемы
достоверности — как JSON и самодостаточный HTML-отчёт, и возвращает оба файла. Они ложатся в
`framehud/` во внешнем каталоге файлов приложения, так что CI забирает их без root; `shareSession`
открывает с ними системное окно «Поделиться». Ничего никуда не загружается.

```kotlin
val export = FrameHud.exportSession(timeoutMs = 5_000) ?: return
FrameHud.shareSession(activity, export)
```

```
adb pull /sdcard/Android/data/<package>/files/framehud/
```

Приложение может положить свои диагностические файлы рядом с возвращёнными.

## Управление через adb

Отлаживаемая сборка с `framehud-metrics` отвечает на броадкасты, так что QA-сборка начинает
отчитываться, помечает сценарий и забирает результат без пересборки:

```
adb shell am broadcast -a com.timkrest.framehud.ENABLE <package>
adb shell am broadcast -a com.timkrest.framehud.SCREEN --es name "product/{id}" <package>
adb shell am broadcast -a com.timkrest.framehud.MARK --es name checkout <package>
adb shell am broadcast -a com.timkrest.framehud.CONTEXT --es scenario smoke <package>
adb shell am broadcast -a com.timkrest.framehud.EXPORT <package>
```

`DISABLE` и `RESET` дополняют набор. Без `--es name` экран или метка сбрасываются, `CONTEXT` без
extras очищает контекст. `EXPORT` отвечает путём к отчёту в результате броадкаста — сразу под
`adb pull`. Сборка без флага отладки игнорирует все команды.

## В системном трейсе

Пока пишется системный трейс, экраны и метки видны как секции `framehud:screen:<имя>` и
`framehud:mark:<имя>`, всплеск jank — как `framehud:jank_burst`, а процент jank, p95, замёрзшие
кадры и heap — как счётчики `framehud.*`. `TraceSectionMetric` из Macrobenchmark меряет именованные
секции. FrameHUD только оставляет маркеры; запись и анализ трейса остаются за Perfetto.

## Потерянное время

`SessionStats.lostTimeMs` — сумма того, насколько опоздавшие кадры вышли за дедлайн. Jank-процент
считает кадр на 18 мс и кадр на 300 мс одинаково, по одному плохому кадру; потерянное время эту
разницу сохраняет, и два экрана с одинаковыми 5% jank перестают выглядеть одинаково.

Кадры, уложившиеся в бюджет, не добавляют ничего. Панель показывает отдельную строку, когда он выше
нуля, оба отчёта несут его по сессии и по экрану, а `JankThresholds(maxLostTimeMs = 500f)` роняет по
нему тест.

## Достоверность замера

`SessionStats.confidence` перечисляет, что мешало сбору: потерянные отчёты `FrameMetrics`,
слушатель событий, занявший поток метрик, троттлинг, энергосбережение или заряд не выше 15%,
смена герцовки, эмулятор, слишком короткая выборка. Каждая проблема называет цифры, которые она
портит. Эмулятор портит только фазы render-потока и GPU. Смена герцовки — jank-процент, потерянное
время и серию.
Короткая выборка — перцентили, на которые не хватает кадров: p99 меньше 300 кадров, p95 и
jank-процент меньше 60, p50 меньше 20. Остальные портят все цифры.

JSON-экспорт несёт проблемы для сессии и экрана, HTML-отчёт их перечисляет, а сводки
`ScreenEnded`/`MarkEnded` заканчиваются пометкой `(suspect measurement)`.

## Падение тестов из-за jank

```kotlin
androidTestImplementation("com.timkrest:framehud-instrumentation:0.8.0")
```

```kotlin
@get:Rule val noJank = DetectJankAfterTestSuccess(JankThresholds(maxJankPercent = 2f))
```

Правило сбрасывает сборщик перед каждым тестом и проверяет пороги после того, как тест прошёл — так
упавший тест остаётся со своей ошибкой. Итоги сессии переживают закрытие панели, поэтому цифры есть
и после того, как `ActivityScenario` закрыл activity.

Порог, чью цифру портит проблема достоверности, не может честно ни пройти, ни упасть, поэтому гейт
считает прогон inconclusive и пишет и саму цифру, и проблему. По умолчанию `OnInconclusive.FAIL`
валит тест, `OnInconclusive.WARN` пишет сообщение в лог и пропускает. Нарушенный порог с чистой
цифрой падает в обоих режимах.

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
- [Сравнение инструментов](docs/comparison.ru.md) — чем FrameHUD отличается от JankStats,
  Macrobenchmark, Perfetto и Play Vitals
- [Справочник API](https://javadoc.io/doc/com.timkrest/framehud-metrics) — генерируется из исходников
  каждого релиза
- [Карта развития](ROADMAP.ru.md) — что планируется дальше и что сделано не будет
- [Список изменений](CHANGELOG.md) — что поменялось в каждом релизе
- [Как участвовать](CONTRIBUTING.md) — как собрать проект и что проверить перед pull request. Вклад
  покрывается [CLA](CLA.md) — бот попросит его подписать.

## Лицензия

Apache 2.0. См. [LICENSE](LICENSE) и [NOTICE](NOTICE).
