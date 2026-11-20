package una.codice.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import una.codice.core.designsystem.motion.AuroraParallax
import una.codice.core.designsystem.motion.LocalAuroraParallax
import una.codice.core.designsystem.motion.rememberMotionEnabled
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent

/**
 * Qué tan intensos son los tres brillos del fondo, de 0 a 1 en su centro. Sube las opacidades para un fondo más vivo;
 * los colores no se tocan aquí: salen del acento del rol (estudiante o profesor).
 */
object AuroraGlow {
    const val Top = 0.30f
    const val Middle = 0.20f
    const val Bottom = 0.14f
}

/**
 * Fondo de la app: el negro azulado de siempre con tres brillos difuminados que se desplazan muy despacio y "respiran"
 * (suben y bajan de intensidad), como una aurora. Los colores son los del acento del rol: verde menta, turquesa y lima
 * para el estudiante; índigo, magenta y violeta para el profesor.
 *
 * Una pantalla con desplazamiento puede mover el fondo a otra velocidad que sus tarjetas con
 * [una.codice.core.designsystem.motion.AuroraParallaxEffect]: cada brillo se mueve distinto, lo que da profundidad.
 *
 * Se dibuja con degradados radiales, que son baratos; no usa `blur`, que en teléfonos modestos pesa mucho. Si el
 * usuario quitó las animaciones del sistema, los brillos quedan quietos.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val parallax = remember { AuroraParallax() }
    val drift: State<Float>
    val pulse: State<Float>
    if (rememberMotionEnabled()) {
        val transition = rememberInfiniteTransition(label = "aurora")
        drift = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "aurora-drift",
        )
        // La respiración va a otro ritmo que el desplazamiento: así el cuadro nunca se repite igual.
        pulse = transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 4_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "aurora-pulse",
        )
    } else {
        drift = remember { mutableFloatStateOf(0.5f) }
        pulse = remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .background(CodiceColors.Canvas)
            // Todo se lee aquí, en el dibujo: cada fotograma solo redibuja, no recompone nada.
            .drawBehind {
                val p = drift.value
                val breathe = pulse.value
                val scroll = parallax.offsetPx
                val radius = size.maxDimension * 0.8f
                // Cada brillo sigue al desplazamiento a distinta velocidad (0.30, 0.18 y 0.08): el de arriba "se va"
                // más rápido que el de abajo, y esa diferencia es la profundidad.
                glow(
                    accent.tertiary.copy(alpha = (AuroraGlow.Top * breathe).coerceIn(0f, 1f)),
                    Offset(size.width * (0.10f + 0.40f * p), size.height * (0.05f + 0.12f * p) - scroll * 0.30f),
                    radius,
                )
                glow(
                    accent.secondary.copy(alpha = (AuroraGlow.Middle * breathe).coerceIn(0f, 1f)),
                    Offset(size.width * (1.00f - 0.45f * p), size.height * (0.32f + 0.14f * p) - scroll * 0.18f),
                    radius * 0.9f,
                )
                glow(
                    accent.primary.copy(alpha = (AuroraGlow.Bottom * breathe).coerceIn(0f, 1f)),
                    Offset(size.width * (0.25f + 0.35f * (1f - p)), size.height * (1.02f - 0.14f * p) - scroll * 0.08f),
                    radius * 0.75f,
                )
            },
    ) {
        CompositionLocalProvider(LocalAuroraParallax provides parallax) { content() }
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) {
    drawRect(Brush.radialGradient(colors = listOf(color, Color.Transparent), center = center, radius = radius))
}
