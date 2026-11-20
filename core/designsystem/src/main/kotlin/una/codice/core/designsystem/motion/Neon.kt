package una.codice.core.designsystem.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import una.codice.core.designsystem.theme.LocalAccent

/** Un degradado en abanico (como un radar) que se puede girar: el borde neón gira, el rectángulo no. */
private class RotatingSweep(
    private val colors: List<Color>,
    private val degrees: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val shader = SweepGradientShader(size.center, colors)
        val matrix = android.graphics.Matrix().apply { setRotate(degrees, size.width / 2f, size.height / 2f) }
        shader.setLocalMatrix(matrix)
        return shader
    }
}

/**
 * Un borde de luz neón que recorre el contorno del elemento girando despacio, con un halo suave. Usa los tres colores
 * del acento del rol, así que en un profesor corre violeta y magenta y en un estudiante lima y turquesa. Va después
 * del `clip` del elemento: la mitad exterior del trazo queda recortada y se ve solo la interior.
 * Con las animaciones del sistema desactivadas, el borde queda quieto.
 */
fun Modifier.neonBorder(cornerRadius: Dp = 16.dp, width: Dp = 1.5.dp): Modifier = composed {
    val accent = LocalAccent.current
    val angle: State<Float> = if (rememberMotionEnabled()) {
        rememberInfiniteTransition(label = "neon").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 7000, easing = LinearEasing)),
            label = "neon-angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    drawWithContent {
        drawContent()
        val brush = RotatingSweep(listOf(accent.primary, accent.secondary, accent.tertiary, accent.primary), angle.value)
        val radius = CornerRadius(cornerRadius.toPx())
        val stroke = width.toPx()
        // Primero un trazo ancho y tenue (el halo) y encima la línea nítida.
        drawRoundRect(brush, cornerRadius = radius, alpha = 0.22f, style = Stroke(stroke * 4f))
        drawRoundRect(brush, cornerRadius = radius, style = Stroke(stroke))
    }
}
