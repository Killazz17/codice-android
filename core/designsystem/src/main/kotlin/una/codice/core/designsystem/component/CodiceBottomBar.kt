package una.codice.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import una.codice.core.designsystem.motion.CodiceMotion
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent

/**
 * Un tab de [CodiceBottomBar]: la ruta que abre al tocarlo, su ícono y su
 * etiqueta.
 */
data class NavTab(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

/**
 * La barra de navegación inferior de Códice.
 * Barra flotante con fondo de cristal y un borde que toma los colores del rol. El realce del tab activo es una sola
 * píldora de degradado que se desliza de un tab al siguiente (con un pequeño rebote) en vez de aparecer y desaparecer;
 * el ícono activo crece y cambia de color suave.
 *
 * @param tabs Los tabs a mostrar.
 * @param currentRoute La ruta actualmente visible.
 * @param onTabSelected Se invoca con la ruta tocada.
 * @param modifier Modificador opcional.
 */
@Composable
fun CodiceBottomBar(
    tabs: List<NavTab>,
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(26.dp)
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }
    // El índice como Float: al cambiar de tab la píldora recorre el camino en vez de saltar.
    val indicator by animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "tab-indicator",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
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
                        accent.primary.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.06f),
                        accent.secondary.copy(alpha = 0.45f),
                    )
                ),
                shape = shape,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
            // La píldora se dibuja aquí, después del padding: abarca exactamente el ancho de los tabs. `indicator`
            // se lee al dibujar, así que cada fotograma de la animación solo redibuja, no recompone.
            .drawBehind {
                if (selectedIndex >= 0) {
                    val tabWidth = size.width / tabs.size
                    val topLeft = Offset(indicator * tabWidth, 0f)
                    val pill = Size(tabWidth, size.height)
                    val corner = CornerRadius(16.dp.toPx())
                    // Un halo ancho y tenue alrededor de la píldora: el brillo de luz neón bajo el tab activo.
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(accent.primary, accent.secondary)),
                        topLeft = topLeft,
                        size = pill,
                        cornerRadius = corner,
                        alpha = 0.18f,
                        style = Stroke(7.dp.toPx()),
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(accent.primary.copy(alpha = 0.22f), accent.secondary.copy(alpha = 0.22f))),
                        topLeft = topLeft,
                        size = pill,
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(accent.primary.copy(alpha = 0.75f), accent.secondary.copy(alpha = 0.75f))),
                        topLeft = topLeft,
                        size = pill,
                        cornerRadius = corner,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            },
    ) {
        tabs.forEach { tab ->
            NavigationBarTab(
                tab = tab,
                selected = tab.route == currentRoute,
                onClick = { onTabSelected(tab.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.NavigationBarTab(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) LocalAccent.current.primary else CodiceColors.TextLow,
        animationSpec = tween(CodiceMotion.StandardMs),
        label = "tab-color",
    )
    // Rebote al activarse: el ícono sobrepasa un poco su tamaño final y se asienta.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "tab-icon",
    )

    Column(
        modifier = modifier
            .heightIn(min = 48.dp) // Criterio de aceptación: min 48dp
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                this.role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(text = tab.label, color = contentColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CodiceBottomBarPreview() {
    val tabs = listOf(
        NavTab("home", Icons.Default.Home, "Inicio"),
        NavTab("courses", Icons.Default.Book, "Cursos"),
        NavTab("notes", Icons.Default.Note, "Apuntes"),
        NavTab("profile", Icons.Default.Person, "Perfil")
    )
    CodiceBottomBar(
        tabs = tabs,
        currentRoute = "home",
        onTabSelected = {}
    )
}
