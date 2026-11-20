package una.codice.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import una.codice.core.designsystem.motion.neonBorder
import una.codice.core.designsystem.theme.CodiceColors

/**
 * Tarjeta de cristal: un degradado sutil de arriba hacia abajo, un borde que brilla más en la esquina superior y
 * esquinas de 16.dp. Es la base de las tarjetas de curso, de apunte y de subtema. Como es algo translúcida, deja ver
 * el tinte del fondo aurora.
 *
 * @param modifier Modificador para personalizar la tarjeta.
 * @param contentPadding Padding interno del contenido.
 * @param onClick Si se pasa, la tarjeta es tocable y se "hunde" un poco mientras se mantiene presionada.
 * @param glow Si es `true`, el borde es una luz neón con los colores del rol que gira alrededor de la tarjeta.
 *   Para las tarjetas protagonistas de una pantalla; en todas a la vez cansaría y gastaría batería.
 * @param content Contenido de la tarjeta.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    glow: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "card-press",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        CodiceColors.GlassHigh.copy(alpha = 0.90f),
                        CodiceColors.Glass.copy(alpha = 0.85f),
                    )
                )
            )
            .then(
                if (glow) {
                    Modifier.neonBorder(cornerRadius = 16.dp)
                } else {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.10f),
                            )
                        ),
                        shape = shape
                    )
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * El aspecto de [GlassCard] (degradado de cristal y borde luminoso) como modificador, para contenedores que no pueden
 * ser una `GlassCard` porque ya tienen su propia estructura. Aplícalo antes del `padding` interno.
 */
fun Modifier.glassPanel(cornerRadius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    CodiceColors.GlassHigh.copy(alpha = 0.90f),
                    CodiceColors.Glass.copy(alpha = 0.85f),
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.10f),
                )
            ),
            shape = shape,
        )
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GlassCardPreview() {
    GlassCard {
        Text(text = "Contenido de la tarjeta", color = CodiceColors.TextHigh)
    }
}
