package sh.atman.beam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import sh.atman.beam.ui.BeamApp
import sh.atman.beam.ui.theme.BeamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BeamTheme {
                BeamApp()
            }
        }
    }
}
