package una.codice.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object CodiceColors {
    val Canvas = Color(0xFF0A0C10)
    val Deep = Color(0xFF101319)
    val Glass = Color(0xFF1A1F28)
    val GlassHigh = Color(0xFF232935)
    val Border = Color(0xFF2C333F)

    val TextHigh = Color(0xFFE2E8F0)
    val TextMid = Color(0xFF94A3B8)

    /**
     * Texto bajo / placeholder — usado para el pie de tarjeta, encabezados de
     * sección y placeholders de búsqueda.
     *
     * Aclarado de `#5E6875` a `#7E88A0` (auditoría de accesibilidad,
     * 2026-08-26): el valor original daba 2.9:1 sobre [Glass] y 3.5:1 sobre
     * [Canvas], por debajo del mínimo de 4.5:1 de WCAG 2.1 AA para texto
     * normal — casi la mitad del contraste requerido. `#7E88A0` da ~4.65:1
     * sobre [Glass] (el fondo más exigente de los dos) manteniendo la misma
     * familia de gris azulado y la jerarquía `TextHigh > TextMid > TextLow`.
     */
    val TextLow = Color(0xFF7E88A0)

    val Lime = Color(0xFFC8F250)
    val Blue = Color(0xFF5D9BFF)
    val Yellow = Color(0xFFF5C451)
    val Amber = Yellow
    val Orange = Color(0xFFFF7A5C)
    val Coral = Orange
    val Purple = Color(0xFFA78BFA)
    val Violet = Purple
}
