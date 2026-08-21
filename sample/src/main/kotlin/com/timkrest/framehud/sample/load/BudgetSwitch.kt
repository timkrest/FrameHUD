package com.timkrest.framehud.sample.load

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.sample.SampleFrameHud
import com.timkrest.framehud.sample.ui.SampleSwitch

@Composable
fun BudgetSwitch(modifier: Modifier = Modifier) {
    val strict by SampleFrameHud.strictBudgets.collectAsStateWithLifecycle()

    SampleSwitch(
        title = "Judge frames by a budget",
        subtitle = "The same frames, judged against ${SampleFrameHud.strictBudgetsLabel} " +
            "instead of the display deadline.",
        checked = strict,
        onCheckedChange = SampleFrameHud::setStrictBudgets,
        modifier = modifier,
    )
}
