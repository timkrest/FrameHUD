package com.timkrest.framehud.internal

import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.BaselineTrust
import com.timkrest.framehud.BudgetCandidate
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.MeasuredMetric

internal const val BASELINE_SCHEMA_VERSION = 2

internal fun Baseline.toJson(): String = buildJsonObject {
    put(SCHEMA, BASELINE_SCHEMA_VERSION)
    putObject(ENVIRONMENT) { putEnvironment(environment) }
    putArray(INTERVALS) {
        for ((id, entry) in entries) {
            addObject {
                put(INTERVAL, id.key())
                put(RUNS, entry.runs)
                put(P50_MS, entry.p50FrameMs)
                put(P95_MS, entry.p95FrameMs)
                put(P99_MS, entry.p99FrameMs)
                put(JANK_PERCENT, entry.jankPercent)
                put(LOST_TIME_MS_PER_FRAME, entry.lostTimeMsPerFrame)
                put(FROZEN_PERCENT, entry.frozenPercent)
                if (entry.trust.cleanRuns.isNotEmpty()) {
                    putObject(CLEAN_RUNS) {
                        for ((metric, count) in entry.trust.cleanRuns) put(metric.name, count)
                    }
                }
                entry.trust.gpuRuns?.let { put(GPU_RUNS, it) }
                entry.frameBudgetMs?.let { put(FRAME_BUDGET_MS, it) }
                entry.trust.candidateBudget?.let { candidate ->
                    putObject(CANDIDATE_BUDGET) {
                        put(BUDGET_MS, candidate.budgetMs)
                        put(RUNS, candidate.runs)
                    }
                }
                putObject(PHASES) { putPhaseAverages(entry.phases) }
            }
        }
    }
}

internal fun parseBaseline(text: String): Parsed<Baseline?> {
    val root = parseJson(text) ?: return rejected("it is not valid JSON")
    val schema = root.int(SCHEMA) ?: return rejected("it names no schema")
    if (schema != BASELINE_SCHEMA_VERSION) {
        return Parsed.Unreadable("it holds schema $schema, this build reads $BASELINE_SCHEMA_VERSION")
    }
    val environment = root.obj(ENVIRONMENT)?.environment() ?: return rejected("it names no environment")
    val intervals = (root.member(INTERVALS) as? JsonValue.Arr)?.items ?: return rejected("it lists no intervals")
    val entries = LinkedHashMap<IntervalId, BaselineEntry>()
    for (item in intervals) {
        if (item !is JsonValue.Obj) return rejected("it holds a broken interval")
        val key = item.string(INTERVAL) ?: return rejected("it holds an unnamed interval")
        val id = intervalId(key) ?: continue
        val entry = item.entry() ?: return rejected("its ${id.label} entry is broken")
        if (entries.put(id, entry) != null) return rejected("it holds ${id.label} twice")
    }
    return Parsed.Read(Baseline(environment = environment, entries = entries))
}

private fun rejected(reason: String): Parsed<Nothing> = Parsed.Rejected(reason)

private fun JsonValue.entry(): BaselineEntry? = readOrNull {
    val candidateBudget = when (val candidate = member(CANDIDATE_BUDGET)) {
        null -> null
        is JsonValue.Obj -> BudgetCandidate(
            budgetMs = candidate.int(BUDGET_MS) ?: return@readOrNull null,
            runs = candidate.int(RUNS) ?: return@readOrNull null,
        )
        else -> return@readOrNull null
    }
    BaselineEntry.restored(
        runs = int(RUNS) ?: return@readOrNull null,
        p50FrameMs = float(P50_MS) ?: return@readOrNull null,
        p95FrameMs = float(P95_MS) ?: return@readOrNull null,
        p99FrameMs = float(P99_MS) ?: return@readOrNull null,
        jankPercent = float(JANK_PERCENT) ?: return@readOrNull null,
        lostTimeMsPerFrame = float(LOST_TIME_MS_PER_FRAME) ?: return@readOrNull null,
        frozenPercent = float(FROZEN_PERCENT) ?: return@readOrNull null,
        phases = obj(PHASES)?.phaseAverages() ?: return@readOrNull null,
        frameBudgetMs = optionalInt(FRAME_BUDGET_MS),
        trust = BaselineTrust(
            cleanRuns = cleanRuns() ?: return@readOrNull null,
            gpuRuns = optionalInt(GPU_RUNS),
            candidateBudget = candidateBudget,
        ),
    )
}

private fun JsonValue.optionalInt(name: String): Int? {
    if (member(name) == null) return null
    return requireNotNull(int(name)) { "$name holds no whole number" }
}

private fun JsonValue.cleanRuns(): Map<MeasuredMetric, Int>? {
    val listed = member(CLEAN_RUNS) ?: return emptyMap()
    if (listed !is JsonValue.Obj) return null
    return buildMap {
        for (name in listed.members.keys) {
            val metric = measuredMetric(name) ?: return null
            put(metric, listed.int(name) ?: return null)
        }
    }
}

private fun measuredMetric(name: String?): MeasuredMetric? = MeasuredMetric.entries.firstOrNull { it.name == name }

private const val SCHEMA = "schema"
private const val ENVIRONMENT = "environment"
private const val INTERVALS = "intervals"
private const val INTERVAL = "interval"
private const val RUNS = "runs"
private const val P50_MS = "p50FrameMs"
private const val P95_MS = "p95FrameMs"
private const val P99_MS = "p99FrameMs"
private const val JANK_PERCENT = "jankPercent"
private const val LOST_TIME_MS_PER_FRAME = "lostTimeMsPerFrame"
private const val FROZEN_PERCENT = "frozenPercent"
private const val CLEAN_RUNS = "cleanRuns"
private const val GPU_RUNS = "gpuRuns"
private const val FRAME_BUDGET_MS = "frameBudgetMs"
private const val CANDIDATE_BUDGET = "candidateBudget"
private const val BUDGET_MS = "budgetMs"
private const val PHASES = "phases"
