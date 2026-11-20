package una.codice.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import una.codice.core.designsystem.motion.CodiceMotion
import una.codice.core.designsystem.motion.rememberMotionEnabled
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent

/**
 * Barra de progreso horizontal con porcentaje. Al aparecer se llena desde 0 y el porcentaje sube contando; si el
 * valor cambia después, se desliza al nuevo. Con las animaciones del sistema desactivadas muestra el valor directo.
 * Se usa en cobertura por módulo.
 *
 * @param progress Progreso entre 0.0 y 1.0.
 * @param modifier Modificador opcional.
 * @param label Etiqueta opcional (ej: "Mód. 1").
 * @param color Color de la barra. Si es `null`, usa el degradado del acento del rol.
 * @param showValue Si muestra el porcentaje junto a la barra. Se apaga cuando la pantalla ya lo escribe en otro lado
 *   (el título de la fila o la cifra grande), para que el número no salga repetido.
 */
@Composable
fun Meter(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color? = null,
    showValue: Boolean = true,
) {
    val accent = LocalAccent.current
    val target = progress.coerceIn(0f, 1f)
    val motion = rememberMotionEnabled()
    val shown = remember { Animatable(if (motion) 0f else target) }
    LaunchedEffect(target, motion) {
        if (motion) {
            shown.animateTo(target, tween(CodiceMotion.EmphasizedMs, easing = CodiceMotion.Settle))
        } else {
            shown.snapTo(target)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (label != null || showValue) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = CodiceColors.TextLow
                )
            }
            if (showValue) Text(
                // Redondea, no trunca: el resto de las pantallas de cobertura redondean, y un 28,6 % no puede verse
                // como "28%" en la barra y "29%" en el resumen.
                text = "${(shown.value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = CodiceColors.TextHigh
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(CodiceColors.GlassHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown.value)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(100.dp))
                    // El extremo izquierdo más tenue da la sensación de que la barra "brilla" hacia adelante.
                    .background(color?.let { Brush.horizontalGradient(listOf(it.copy(alpha = 0.6f), it)) } ?: accent.brush())
            )
        }
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MeterPreview() {
    Column(
        modifier = Modifier.padding(16.dp).width(300.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Meter(progress = 0.98f, label = "Mód. 1", color = CodiceColors.Lime)
        Meter(progress = 0.58f, label = "Mód. 4", color = CodiceColors.Yellow)
        Meter(progress = 0.25f, label = "Mód. 5", color = CodiceColors.Orange)
    }
}
