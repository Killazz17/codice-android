package una.codice.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import una.codice.core.designsystem.theme.CodiceColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLES = 70
private const val DURATION_MS = 1800
private const val GRAVITY_DP = 1900f

private class Particle(
    val angle: Float,
    val speedDp: Float,
    val sizeDp: Float,
    val spin: Float,
    val color: Color,
    val round: Boolean,
)

private val defaultConfettiColors = listOf(
    CodiceColors.Lime,
    CodiceColors.Blue,
    CodiceColors.Purple,
    CodiceColors.Yellow,
    CodiceColors.Coral,
)

/**
 * Una ráfaga de confeti que sale hacia arriba desde el centro y cae. Se dispara cada vez que [trigger] cambia a un
 * valor mayor que 0 (súbelo en uno para celebrar de nuevo). Va encima del contenido, sin interceptar toques, y no
 * dibuja nada fuera de la animación. Con las animaciones del sistema desactivadas no hace nada.
 */
@Composable
fun ConfettiBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
    colors: List<Color> = defaultConfettiColors,
) {
    val motion = rememberMotionEnabled()
    val progress = remember { Animatable(1f) } // 1 = terminado: en reposo no se dibuja nada.
    val particles = remember(trigger, colors) {
        val random = Random(trigger)
        List(PARTICLES) {
            Particle(
                // Cono hacia arriba: de -160° a -20° (el eje Y de la pantalla crece hacia abajo).
                angle = Math.toRadians(-160.0 + random.nextDouble() * 140.0).toFloat(),
                speedDp = 420f + random.nextFloat() * 620f,
                sizeDp = 5f + random.nextFloat() * 6f,
                spin = (random.nextFloat() - 0.5f) * 900f,
                color = colors[random.nextInt(colors.size)],
                round = random.nextBoolean(),
            )
        }
    }

    LaunchedEffect(trigger, motion) {
        if (trigger > 0 && motion) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        }
    }

    if (progress.value < 1f) {
        Canvas(modifier = modifier) {
            val seconds = progress.value * DURATION_MS / 1000f
            // Se desvanece en el último tercio del recorrido.
            val alpha = ((1f - progress.value) / 0.35f).coerceIn(0f, 1f)
            val origin = Offset(size.width / 2f, size.height * 0.35f)
            val dp = 1.dp.toPx()

            particles.forEach { p ->
                val x = origin.x + cos(p.angle) * p.speedDp * dp * seconds
                val y = origin.y + sin(p.angle) * p.speedDp * dp * seconds + 0.5f * GRAVITY_DP * dp * seconds * seconds
                val side = p.sizeDp * dp
                rotate(degrees = p.spin * seconds, pivot = Offset(x, y)) {
                    if (p.round) {
                        drawCircle(p.color.copy(alpha = alpha), radius = side / 2f, center = Offset(x, y))
                    } else {
                        drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - side / 2f, y - side / 4f), size = Size(side, side / 2f))
                    }
                }
            }
        }
    }
}
