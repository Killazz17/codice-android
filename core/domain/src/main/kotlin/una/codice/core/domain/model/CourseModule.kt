package una.codice.core.domain.model

/**
 * Un módulo del temario: el agrupador de primer nivel del programa del curso
 * (por ejemplo, *Módulo 4 · Programación distribuida*).
 *
 * Corresponde a la tabla `module` del modelo de datos (`CODICE_2.HTM#ddl`).
 *
 * ### Por qué se llama `CourseModule` y no `Module`
 *
 * `Module` chocaría de frente con `dagger.Module` en cualquier archivo que
 * use Hilt, obligando a importar con alias o a calificar el nombre completo
 * cada vez. El prefijo cuesta seis letras y evita ese ruido en todo el
 * proyecto.
 *
 * @property number Número del módulo dentro del curso (`4`). Único por curso
 *   — es la restricción `UNIQUE (course_id, number)` del DDL, y la valida
 *   [una.codice.core.domain.usecase.syllabus.ValidateSyllabusUseCase].
 * @property confidence Qué tan segura estaba la IA de este módulo al extraerlo del PDF (0 a 1). Un
 *   módulo escrito a mano o leído del servidor es `1`: no hay nada que dudar.
 */
data class CourseModule(
    val id: String,
    val number: Int,
    val title: String,
    val subtopics: List<Subtopic> = emptyList(),
    val confidence: Float = 1f,
) {
    /** La IA dudó de este módulo: el profesor debería revisarlo antes de publicar. */
    val isLowConfidence: Boolean
        get() = confidence < SYLLABUS_REVIEW_THRESHOLD
}
