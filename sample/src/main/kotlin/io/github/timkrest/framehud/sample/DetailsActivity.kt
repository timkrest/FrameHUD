package io.github.timkrest.framehud.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

/**
 * A second screen, so the panel can be watched across an activity swap: a system overlay survives
 * it, an app window is torn down and rebuilt. Leaving this screen also ends its collection, which
 * sends a per-screen summary to every listener.
 */
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
