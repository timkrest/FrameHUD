package com.timkrest.framehud.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.sample.readouts.MetricsReadout
import com.timkrest.framehud.sample.ui.SampleCard
import com.timkrest.framehud.sample.ui.SampleHeader
import com.timkrest.framehud.sample.ui.SampleLine
import com.timkrest.framehud.sample.ui.SampleNote
import kotlinx.coroutines.delay

class DetailsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SampleFrameHud.listen()
        val rowIndex = intent.getIntExtra(EXTRA_ROW_INDEX, 0)
        setContent {
            MaterialTheme {
                RowDetails(rowIndex = rowIndex)
            }
        }
    }

    companion object {
        fun intent(context: Context, rowIndex: Int): Intent =
            Intent(context, DetailsActivity::class.java).putExtra(EXTRA_ROW_INDEX, rowIndex)
    }
}

@Composable
private fun RowDetails(rowIndex: Int) {
    MeasuredScreen(name = ROW_SCREEN)
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(rowIndex) {
        delay(ROW_LOAD_MS)
        loaded = true
        FrameHud.reportUsable()
    }

    Scaffold { insets ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SampleHeader(
                title = "Row $rowIndex",
                subtitle = "A screen the app names itself, and reports usable once its data is in.",
            )
            if (loaded) {
                SampleCard(title = "Measured as") {
                    SampleLine(label = "screen", value = ROW_SCREEN)
                    SampleNote(
                        text = "One name for every row, so a trace and a report keep them together " +
                            "instead of splitting them by id.",
                    )
                }
                MetricsReadout()
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private const val EXTRA_ROW_INDEX = "row_index"
private const val ROW_SCREEN = "row/{index}"
private const val ROW_LOAD_MS = 400L
