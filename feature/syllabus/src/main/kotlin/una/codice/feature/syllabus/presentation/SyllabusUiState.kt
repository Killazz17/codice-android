package una.codice.feature.syllabus.presentation

import una.codice.core.designsystem.component.CourseOption
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.usecase.syllabus.SyllabusIssue

/**
 * Todo lo que la pantalla de temario necesita para dibujarse.
 *
 * ### Por qué un `data class` y no un `sealed interface`, al revés que `CoverageUiState`
 *
 * En el mapa de cobertura los estados son excluyentes: o carga, o está
 * vacío, o falló, o hay datos. Aquí no: el profesor puede estar **editando**
 * el temario mientras se guarda un borrador de fondo y a la vez hay
 * advertencias del último intento de publicar. Son dimensiones
 * independientes, así que forzarlas a un solo estado sellado obligaría a
 * inventar combinaciones ("EditandoMientrasGuardaConAdvertencias") que no
 * escalan.
 *
 * @property isLoading Solo la carga inicial. Guardar y publicar tienen sus
 *   propias banderas, para que la lista no parpadee a "cargando" cada vez
 *   que se guarda.
 * @property issues Resultado de la última validación. Vacío mientras el
 *   profesor no haya intentado publicar.
 * @property justPublished Se pone en `true` un instante tras publicar con
 *   éxito, para que la pantalla muestre la confirmación.
 */
data class SyllabusUiState(
    val syllabus: Syllabus = Syllabus(courseId = ""),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isPublishing: Boolean = false,
    val isExtractingAi: Boolean = false,

    /** El último PDF falló por la IA o la red y se puede volver a intentar con el mismo archivo. */
    val canRetryExtraction: Boolean = false,
    val aiConfidenceMessage: String? = null,
    val issues: List<SyllabusIssue> = emptyList(),
    val errorMessage: String? = null,
    val justPublished: Boolean = false,
    val availableCourses: List<CourseOption> = emptyList(),
    val selectedCourseId: String = "",
    val uploadedFileName: String? = null,
    val uploadedFileSize: String? = null,

    /** Los módulos y subtemas que el profesor ya corrigió o dio por buenos: dejan de marcarse como dudosos. */
    val reviewedIds: Set<String> = emptySet(),

    /** El servidor ya tiene un temario de este curso: publicar el borrador lo actualizará. */
    val serverHasSyllabus: Boolean = false,

    /** Se pide confirmar antes de publicar encima del temario que ya tiene el curso. */
    val isConfirmingOverwrite: Boolean = false,
) {
    /**
     * Un módulo se marca en ámbar cuando la IA dudó de él y el profesor todavía no lo ha revisado, o cuando está
     * incompleto: sin título o sin ningún subtema.
     */
    fun needsAttention(module: CourseModule): Boolean =
        (module.isLowConfidence && module.id !in reviewedIds) || module.title.isBlank() || module.subtopics.isEmpty()

    /** Un subtema se marca en ámbar por lo mismo, o cuando no tiene título ni objetivos: sin ellos no se puede auditar. */
    fun needsAttention(subtopic: Subtopic): Boolean =
        (subtopic.isLowConfidence && subtopic.id !in reviewedIds) ||
            subtopic.title.isBlank() ||
            subtopic.objectives.isEmpty()

    /** Cuántos módulos y subtemas están marcados en ámbar, para el resumen de arriba. */
    val itemsToReview: Int
        get() = syllabus.modules.sumOf { module ->
            (if (needsAttention(module)) 1 else 0) + module.subtopics.count { needsAttention(it) }
        }

    /** Problemas que impiden publicar, ya separados para la pantalla. */
    val blockingIssues: List<SyllabusIssue> get() = issues.filter { it.isBlocking }

    /** Avisos que no impiden publicar. */
    val warnings: List<SyllabusIssue> get() = issues.filter { !it.isBlocking }

    /**
     * Si el botón de publicar debe estar habilitado.
     *
     * No depende de [issues]: un temario que nunca se intentó publicar tiene
     * la lista vacía y debe poder intentarse. Lo que sí bloquea es no tener
     * módulos, estar en medio de otra operación, o ya estar publicado sin
     * cambios pendientes.
     */
    val canPublish: Boolean
        get() = !isLoading && !isSaving && !isPublishing && syllabus.modules.isNotEmpty()
}
