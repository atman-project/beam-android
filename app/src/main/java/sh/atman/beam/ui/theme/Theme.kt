package sh.atman.beam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import sh.atman.beam.R

@Composable
fun BeamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val primary = colorResource(R.color.beam_primary)
    val onPrimary = colorResource(R.color.beam_on_primary)
    val primaryContainer = colorResource(R.color.beam_primary_container)
    val onPrimaryContainer = colorResource(R.color.beam_on_primary_container)

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
