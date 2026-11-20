package una.codice.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow

/** Un curso entre los que elegir: [id] del grupo y la etiqueta corta (p. ej. el código `EIF411`). */
data class CourseOption(val id: String, val label: String)

/**
 * Fila de chips para elegir el curso que se ve, compartida por la Biblioteca y el Mapa. No se dibuja
 * con menos de dos cursos: no habría nada que elegir.
 *
 * @param allLabel Si no es `null`, agrega primero un chip con ese texto que selecciona `null` (todos).
 * @param horizontalPadding El margen lateral. Las pantallas que ya tienen su propio margen lo bajan (o lo ponen en 0) para que los
 *   chips queden alineados con el título y no sangrados.
 */
@Composable
fun CourseSelector(
    options: List<CourseOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String? = null,
    horizontalPadding: Dp = 26.dp,
    joinLabel: String? = null,
    onJoin: (() -> Unit)? = null,
) {
    // Sin cursos igual se dibuja si hay "+ Unirme": es justamente lo que hay que ofrecer.
    if (options.isEmpty() && onJoin == null) return
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allLabel != null) {
            item(key = "all") { CourseChip(allLabel, selectedId == null) { onSelect(null) } }
        }
        items(options, key = { it.id }) { option ->
            CourseChip(option.label, option.id == selectedId) { onSelect(option.id) }
        }
        if (onJoin != null && joinLabel != null) {
            item(key = "join") { JoinChip(joinLabel, onJoin) }
        }
    }
}

/** El chip "+ Unirme": agregar un curso es una acción de CURSOS, así que vive junto a ellos, donde se espera encontrarla. */
@Composable
private fun JoinChip(label: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.primary.copy(alpha = 0.12f))
            .border(1.dp, accent.primary.copy(alpha = 0.6f), shape)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = accent.primary, modifier = Modifier.size(16.dp))
        Text(text = label, color = accent.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun CourseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = if (selected) CodiceColors.Canvas else CodiceColors.TextMid,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        // Un nombre de curso largo no debe empujar fuera de la pantalla a los demás chips (ni al "+ Unirme"): se recorta.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(shape)
            // Seleccionado: el degradado del acento del rol. Sin seleccionar: cristal con un borde fino.
            .then(
                if (selected) {
                    Modifier.background(accent.brush())
                } else {
                    Modifier
                        .background(CodiceColors.Glass.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                }
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun CourseSelectorPreview() {
    CourseSelector(
        options = listOf(CourseOption("1", "EIF411"), CourseOption("2", "DEMO01")),
        selectedId = "1",
        onSelect = {},
        allLabel = "Todos los cursos",
    )
}
