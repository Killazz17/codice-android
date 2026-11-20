package una.codice.core.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import una.codice.core.designsystem.motion.CodiceMotion

/**
 * La identidad de color de un rol. Los colores de estado (cubierto, parcial, sin apuntes) NO cambian con el rol:
 * significan lo mismo para todos. Lo que cambia es el "ambiente": botones, selección, brillos del fondo y títulos.
 *
 * @property primary El color principal: selección, acento de texto, el punto de partida de los degradados.
 * @property secondary El compañero del degradado de [primary].
 * @property tertiary Un tercer tono para los brillos del fondo y el borde neón.
 */
@Immutable
data class AccentPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    /** Degradado horizontal de [primary] a [secondary]: botones, barras, chips. */
    fun brush(): Brush = Brush.horizontalGradient(listOf(primary, secondary))
}

object CodiceAccents {
    /** Estudiante: neón lima y turquesa, con verde menta de apoyo (la identidad "verde neón" de la app). */
    val Student = AccentPalette(
        primary = Color(0xFFC8F250),
        secondary = Color(0xFF22E3D0),
        tertiary = Color(0xFF2EE6A6),
    )

    /**
     * Acceso (iniciar sesión y registro): ámbar solar y naranja, con oro suave de apoyo, como un amanecer. Es la puerta
     * de entrada y no pertenece a ningún rol, así que tiene su propia identidad, a propósito distinta del verde del
     * estudiante y del violeta del profesor. Al entrar, el color se funde al del rol.
     */
    val Gateway = AccentPalette(
        primary = Color(0xFFFFB84D),
        secondary = Color(0xFFFF7A45),
        tertiary = Color(0xFFFFD66B),
    )

    /** Profesor: ultravioleta y magenta, con índigo de apoyo. */
    val Teacher = AccentPalette(
        primary = Color(0xFFB388FF),
        secondary = Color(0xFFFF5CAA),
        tertiary = Color(0xFF6C7BFF),
    )
}

/** El acento vigente. Sin proveedor arriba, es el del estudiante. */
val LocalAccent = compositionLocalOf { CodiceAccents.Student }

/**
 * Pone [target] como acento de todo lo que está dentro. Al cambiar (por ejemplo al cerrar sesión de un profesor e
 * iniciar como estudiante) los colores se funden en vez de saltar. Solo se recompone lo que lee [LocalAccent].
 */
@Composable
fun ProvideAccent(
    target: AccentPalette,
    content: @Composable () -> Unit,
) {
    val spec = tween<Color>(CodiceMotion.EmphasizedMs)
    val primary by animateColorAsState(target.primary, spec, label = "accent-primary")
    val secondary by animateColorAsState(target.secondary, spec, label = "accent-secondary")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "accent-tertiary")
    // El color primario de Material también sigue al rol: cualquier widget sin color explícito (una rueda de Material,
    // un interruptor, un botón por defecto) sale con el color del rol y no con el morado por defecto de Material.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = primary),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(
            LocalAccent provides AccentPalette(primary, secondary, tertiary),
            content = content,
        )
    }
}
