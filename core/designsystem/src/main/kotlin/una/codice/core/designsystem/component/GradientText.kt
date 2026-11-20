package una.codice.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import una.codice.core.designsystem.motion.rememberMotionEnabled
import una.codice.core.designsystem.theme.LocalAccent

/**
 * Texto con el degradado del acento del rol. Con [animated] el degradado fluye despacio de izquierda a derecha:
 * un patrón en espejo que se repite, así el bucle no tiene corte. Sirve para los títulos de pantalla y las cifras.
 *
 * El degradado se pinta en la fase de DIBUJO, no en la de composición: el texto se dibuja de un color liso y encima se
 * pinta el degradado con `BlendMode.SrcIn`, que solo tiñe donde hay letras. Así el valor animado se lee al dibujar y
 * el texto no se recompone en cada fotograma. (La versión que pasaba un `Brush` al `TextStyle` leía la animación en la
 * composición; dentro de una pantalla con `verticalScroll` eso dejaba de entregar los toques tras un fling.)
 */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    animated: Boolean = true,
) {
    val accent = LocalAccent.current
    val span = with(LocalDensity.current) { 280.dp.toPx() }
    val shift: State<Float> = if (animated && rememberMotionEnabled()) {
        rememberInfiniteTransition(label = "gradient-text").animateFloat(
            initialValue = 0f,
            // Un periodo completo del patrón en espejo mide dos tramos: así vuelve al inicio sin salto.
            targetValue = span * 2f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing)),
            label = "gradient-text-shift",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Text(
        text = text,
        modifier = modifier
            // Capa aparte: así el SrcIn solo afecta a las letras y no a lo que hay detrás del texto.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(accent.primary, accent.secondary),
                        start = Offset(shift.value, 0f),
                        end = Offset(shift.value + span, 0f),
                        tileMode = TileMode.Mirror,
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
        style = TextStyle(color = Color.White, fontSize = fontSize, fontWeight = fontWeight),
    )
}
