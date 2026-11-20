package una.codice.core.designsystem.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.theme.CodiceColors

/** Una franja de luz que cruza el elemento de izquierda a derecha, para indicar que el contenido se está cargando. */
fun Modifier.shimmer(): Modifier = composed {
    val sweep: State<Float> = if (rememberMotionEnabled()) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
            label = "shimmer-sweep",
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }

    drawWithContent {
        drawContent()
        val start = sweep.value * size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + size.width * 0.6f, size.height * 0.3f),
            ),
        )
    }
}

/** Un bloque gris con brillo: ocupa el lugar del texto o la imagen mientras llegan los datos. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    Box(modifier = modifier.clip(shape).background(CodiceColors.GlassHigh).shimmer())
}

/** Una tarjeta de lista en estado de carga: un título y [lines] renglones más cortos. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    lines: Int = 2,
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(16.dp))
            repeat(lines) { index ->
                SkeletonBlock(Modifier.fillMaxWidth(if (index == lines - 1) 0.4f else 0.85f).height(12.dp))
            }
        }
    }
}
