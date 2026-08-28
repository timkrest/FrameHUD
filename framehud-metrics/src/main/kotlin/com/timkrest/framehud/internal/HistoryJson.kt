package com.timkrest.framehud.internal

import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.RecordedRun

internal const val HISTORY_SCHEMA_VERSION = 1

internal data class StoredRun(val runId: String, val run: RecordedRun)

internal fun List<StoredRun>.toHistoryJson(): String = buildJsonObject {
    put(SCHEMA, HISTORY_SCHEMA_VERSION)
    putArray(RUNS) {
        for (stored in this@toHistoryJson) addObject { putRun(stored) }
    }
}

internal fun parseHistory(text: String): Parsed<List<StoredRun>> {
    val root = parseJson(text) ?: return rejected("it is not valid JSON")
    val schema = root.int(SCHEMA) ?: return rejected("it names no schema")
    if (schema != HISTORY_SCHEMA_VERSION) {
        return Parsed.Unreadable("it holds schema $schema, this build reads $HISTORY_SCHEMA_VERSION")
    }
    val listed = root.member(RUNS) as? JsonValue.Arr ?: return rejected("it lists no runs")
    val runs = listed.items.map { item ->
        item.run() ?: return rejected("it holds a broken run")
    }
    return Parsed.Read(runs)
}

private fun rejected(reason: String): Parsed<Nothing> = Parsed.Rejected(reason)

private fun JsonObjectScope.putRun(stored: StoredRun) {
    val run = stored.run
    put(RUN_ID, stored.runId)
    put(RECORDED_AT_MS, run.recordedAtEpochMs)
    put(APP_VERSION_NAME, run.appVersionName)
    put(APP_VERSION_CODE, run.appVersionCode)
    putObject(ENVIRONMENT) { putEnvironment(run.environment) }
    putArray(INTERVALS) {
        for (report in run.intervals) {
            addObject {
                put(INTERVAL, report.id.key())
                report.frameBudgetMs?.let { put(FRAME_BUDGET_MS, it) }
                putIntervalStats(report.stats)
            }
        }
    }
}

private fun JsonValue.run(): StoredRun? = readOrNull {
    val listed = member(INTERVALS) as? JsonValue.Arr ?: return@readOrNull null
    val runId = string(RUN_ID) ?: return@readOrNull null
    val run = RecordedRun.of(
        recordedAtEpochMs = long(RECORDED_AT_MS) ?: return@readOrNull null,
        environment = obj(ENVIRONMENT)?.environment() ?: return@readOrNull null,
        appVersionName = string(APP_VERSION_NAME),
        appVersionCode = long(APP_VERSION_CODE) ?: return@readOrNull null,
        intervals = listed.items.map { it.report() ?: return@readOrNull null },
    )
    StoredRun(runId, run)
}

private fun JsonValue.report(): IntervalReport? = readOrNull {
    IntervalReport.of(
        id = string(INTERVAL)?.let(::intervalId) ?: return@readOrNull null,
        stats = intervalStats() ?: return@readOrNull null,
        frameBudgetMs = when (member(FRAME_BUDGET_MS)) {
            null -> null
            else -> int(FRAME_BUDGET_MS) ?: return@readOrNull null
        },
    )
}

private const val SCHEMA = "schema"
private const val RUNS = "runs"
private const val RUN_ID = "runId"
private const val RECORDED_AT_MS = "recordedAtMs"
private const val APP_VERSION_NAME = "appVersionName"
private const val APP_VERSION_CODE = "appVersionCode"
private const val ENVIRONMENT = "environment"
private const val INTERVALS = "intervals"
private const val INTERVAL = "interval"
private const val FRAME_BUDGET_MS = "frameBudgetMs"
