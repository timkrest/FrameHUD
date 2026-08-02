package com.timkrest.framehud.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

class DetailsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SampleEvents.register()
        setContent {
            MaterialTheme {
                SampleScreen(
                    title = "Second screen",
                    subtitle = "Go back and the summary for this screen reaches every listener.",
                )
            }
        }
    }
}
