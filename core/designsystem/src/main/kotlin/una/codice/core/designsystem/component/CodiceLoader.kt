package una.codice.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import una.codice.core.designsystem.motion.rememberMotionEnabled
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.designsystem.R
import androidx.compose.ui.res.stringResource

/**
 * La rueda de carga de Códice: un arco con degradado que gira, con los colores del rol (verde para el estudiante,
 * morado y magenta para el profesor). Reemplaza al `CircularProgressIndicator` de Material, que usa un color fijo.
 *
 * El ángulo se lee al dibujar, así que cada fotograma redibuja sin recomponer. Con las animaciones del sistema
 * desactivadas, el arco queda quieto.
 *
 * @param size Diámetro de la rueda.
 * @param strokeWidth Grosor del arco.
 */
@Composable
fun CodiceLoader(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 4.dp,
) {
    val accent = LocalAccent.current
    val angle: State<Float> = if (rememberMotionEnabled()) {
        rememberInfiniteTransition(label = "loader").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
            label = "loader-angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // semantics { } no es @Composable: el texto se resuelve antes, no dentro del bloque.
    val loadingLabel = stringResource(R.string.ds_loading)
    Canvas(modifier = modifier.size(size).semantics { contentDescription = loadingLabel }) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        // El "carril": un anillo muy tenue de fondo, para que la rueda se note aunque el arco esté a medio desvanecerse.
        drawArc(
            color = accent.primary.copy(alpha = 0.16f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke),
        )
        rotate(angle.value) {
            // La cola del arco se desvanece (transparente) y la cabeza es el color principal: parece que "deja estela".
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, accent.secondary, accent.primary), center = center),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
