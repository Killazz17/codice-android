package una.codice.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import una.codice.core.designsystem.motion.rememberMotionEnabled
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent

/**
 * Botón primario con estado de carga (spinner) y estado deshabilitado.
 * CTA = Call To Action.
 *
 * Por defecto se rellena con el degradado del acento del rol (lima y turquesa para el estudiante, violeta y magenta
 * para el profesor), brilla con una sombra de su mismo color y un destello lo cruza de vez en cuando.
 *
 * @param text Texto del botón.
 * @param onClick Acción al hacer clic.
 * @param modifier Modificador opcional.
 * @param enabled Si el botón está habilitado.
 * @param isLoading Si el botón está en estado de carga.
 * @param containerColor Si se pasa, el botón es de ese color sólido en vez del degradado del rol.
 */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color = CodiceColors.Canvas
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Se "hunde" un poco al presionarlo y rebota al soltarlo: la respuesta táctil que el botón plano no da.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "cta-press",
    )

    // El destello cruza el botón en el primer segundo de cada ciclo de 3,6 s y el resto del tiempo descansa.
    val shine: State<Float> = if (enabled && rememberMotionEnabled()) {
        rememberInfiniteTransition(label = "cta-shine").animateFloat(
            initialValue = -0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 3600
                    -0.4f at 0 using LinearEasing
                    1.4f at 1100 using LinearEasing
                    1.4f at 3600
                }
            ),
            label = "cta-shine-x",
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }

    val fill = containerColor?.let { Brush.horizontalGradient(listOf(it, it)) } ?: accent.brush()

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (enabled) 14.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = accent.primary,
                spotColor = accent.primary,
            )
            .clip(shape)
            .alpha(if (enabled) 1f else 0.4f)
            .background(fill)
            .drawBehind {
                val x = shine.value * size.width
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        start = Offset(x - size.width * 0.15f, 0f),
                        end = Offset(x + size.width * 0.15f, size.height * 0.6f),
                    )
                )
            },
        interactionSource = interactionSource,
        enabled = enabled && !isLoading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = CodiceColors.Canvas,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CtaButtonPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CtaButton(text = "Unirme al curso", onClick = {})
        CtaButton(text = "Cargando...", onClick = {}, isLoading = true)
        CtaButton(text = "Deshabilitado", onClick = {}, enabled = false)
    }
}
