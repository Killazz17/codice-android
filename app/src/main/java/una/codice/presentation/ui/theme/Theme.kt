package una.codice.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema base de Codice.
 *
 * ### Estado: provisional a proposito
 *
 * Ahora mismo usa la paleta por defecto de Material 3. La paleta real de
 * Codice, con las tipografias Inter y JetBrains Mono y el tema oscuro, es la
 * tarjeta "Implementar el sistema de diseño" del tablero, y vivira en el modulo
 * :core:designsystem cuando se cree.
 *
 * Se deja este minimo para que la app compile y arranque; no vale la pena
 * inventar colores ahora que habria que borrar despues.
 *
 * ### Por que NO usa dynamicColor
 *
 * Android 12+ ofrece generar la paleta a partir del fondo de pantalla del
 * usuario. Para Codice no sirve: el mapa de cobertura comunica estados
 * (cubierto / parcial / ausente) con colores concretos que deben significar lo
 * mismo en todos los telefonos. Si el sistema los reasigna, se pierde el
 * significado.
 */
private val EsquemaClaro = lightColorScheme()
private val EsquemaOscuro = darkColorScheme()

@Composable
fun CodiceTheme(
    // Por defecto sigue la preferencia del sistema operativo.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EsquemaOscuro else EsquemaClaro,
        content = content,
    )
}
