package una.codice.core.domain.model

/**
 * Un subtema del temario, con los objetivos de aprendizaje que lo definen.
 *
 * Corresponde a las tablas `subtopic` y `subtopic_objective` del modelo de
 * datos (`CODICE_2.HTM#ddl`). Los objetivos van embebidos aquí y no como una
 * entidad aparte porque no tienen identidad propia para el profesor: son
 * simplemente las líneas que describen qué debe saber el estudiante, y
 * siempre se editan, se guardan y se muestran junto a su subtema.
 *
 * ### Por qué importan los objetivos
 *
 * Son la vara contra la que la llamada de IA 3 audita cada apunte: sin
 * objetivos, un subtema no se puede auditar y el mapa de cobertura no puede
 * pasar de [CoverageStatus.NO_NOTES]. Por eso
 * [una.codice.core.domain.usecase.syllabus.ValidateSyllabusUseCase] avisa
 * cuando un subtema queda sin ninguno.
 *
 * @property code Código tal como aparece en el programa del curso, por
 *   ejemplo `"4.4"`. Único dentro de su módulo.
 * @property position Orden dentro del módulo. Es un campo aparte del [code]
 *   a propósito: ordenar por código como texto pondría `"4.10"` antes que
 *   `"4.2"`.
 * @property confidence Qué tan segura estaba la IA de este subtema al extraerlo del PDF (0 a 1). Uno escrito a
 *   mano o leído del servidor es `1`.
 */
data class Subtopic(
    val id: String,
    val code: String,
    val title: String,
    val position: Int,
    val objectives: List<String> = emptyList(),
    val confidence: Float = 1f,
) {
    /** La IA dudó de este subtema: el profesor debería revisarlo antes de publicar. */
    val isLowConfidence: Boolean
        get() = confidence < SYLLABUS_REVIEW_THRESHOLD
}
