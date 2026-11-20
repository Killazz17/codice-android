package una.codice.presentation.lock

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import una.codice.R
import una.codice.core.designsystem.component.AuroraBackground
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent

/**
 * La pantalla de bloqueo: tapa toda la app mientras se espera la huella.
 *
 * Se dibuja ENCIMA del NavHost en vez de reemplazarlo: si lo quitara de la composición, la pila de navegación se
 * perdería y la persona volvería al inicio después de desbloquear. El `pointerInput` se traga los toques para que
 * nada de lo que hay debajo reciba un clic mientras está bloqueado.
 */
@Composable
fun LockOverlay(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    AuroraBackground(modifier = modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(80.dp),
                )
                GradientText(text = stringResource(R.string.app_locked_title), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.app_locked_hint),
                    color = CodiceColors.TextMid,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                CtaButton(text = stringResource(R.string.app_unlock), onClick = onUnlock)
            }
        }
    }
}
