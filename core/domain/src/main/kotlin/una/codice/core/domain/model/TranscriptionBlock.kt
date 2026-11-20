package una.codice.core.domain.model

/**
 * [IA-03] Un bloque de texto transcrito por la IA a partir de la foto del apunte,
 * acompañado de su nivel de confianza y tipo de contenido (texto, código, ecuación, etc.).
 */
data class TranscriptionBlock(
    val text: String,
    val confidence: Float,
    val type: String = "text",
) {
    /**
     * Umbral de revisión obligatoria configurable (< 0.70).
     * Los bloques por debajo de este valor se marcan y el apunte requiere confirmación del estudiante.
     */
    val requiresReview: Boolean get() = confidence < 0.70f
}
