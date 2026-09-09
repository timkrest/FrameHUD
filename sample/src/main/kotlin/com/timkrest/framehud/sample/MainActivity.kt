package com.timkrest.framehud.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SampleFrameHud.listen()
        setContent {
            ReportDrawn()
            SampleTheme {
                SampleApp(onOpenRow = ::openRow)
            }
        }
    }

    private fun openRow(index: Int) {
        startActivity(DetailsActivity.intent(this, index))
    }
}
