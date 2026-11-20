package una.codice.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.R
import androidx.compose.ui.res.stringResource

/**
 * Estado de error con mensaje y botón de reintentar.
 *
 * @param message Mensaje de error.
 * @param onRetry Acción al tocar el botón de reintentar.
 * @param modifier Modificador opcional.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = CodiceColors.Orange,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = CodiceColors.TextHigh,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp) // Criterio de aceptación: min 48dp
        ) {
            Text(
                text = stringResource(R.string.ds_retry),
                style = MaterialTheme.typography.labelLarge,
                color = CodiceColors.Lime,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ErrorStatePreview() {
    ErrorState(
        message = "Ocurrió un error al cargar los datos. Por favor, verificá tu conexión.",
        onRetry = {}
    )
}
