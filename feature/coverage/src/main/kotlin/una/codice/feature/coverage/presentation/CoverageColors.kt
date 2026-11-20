package una.codice.feature.coverage.presentation

import una.codice.core.designsystem.theme.CodiceColors

/**
 * Paleta real de Códice, tomada de Figma (nodo `2:75`, "ESTADOS DEL MAPA DE
 * COBERTURA") — ahora delegando en [CodiceColors] de `:core:designsystem`.
 *
 * Los cuatro colores de estado ([Coral], [Amber], [Lime], [Blue]) son
 * exactamente los que Figma usa para el borde y el punto de cada tarjeta de
 * [una.codice.core.domain.model.CoverageStatus] en esa sección.
 */
object CoverageColors {
    /** Fondo de toda la pantalla. */
    val Canvas = CodiceColors.Canvas

    /** Tarjetas translúcidas sobre el fondo. */
    val Glass = CodiceColors.Glass

    /** Texto de alto énfasis: títulos de módulo y de subtema. */
    val TextHigh = CodiceColors.TextHigh

    /** Texto medio: descripciones, mensajes de estado vacío/error. */
    val TextMid = CodiceColors.TextMid

    /** Texto bajo: etiquetas discretas. */
    val TextLow = CodiceColors.TextLow

    /** [una.codice.core.domain.model.CoverageStatus.COVERED]. */
    val Lime = CodiceColors.Lime

    /** [una.codice.core.domain.model.CoverageStatus.REINFORCED]. */
    val Blue = CodiceColors.Blue

    /** [una.codice.core.domain.model.CoverageStatus.PARTIAL]. */
    val Yellow = CodiceColors.Yellow
    val Amber = CodiceColors.Amber

    /** [una.codice.core.domain.model.CoverageStatus.NO_NOTES]. */
    val Orange = CodiceColors.Orange
    val Coral = CodiceColors.Coral
}
