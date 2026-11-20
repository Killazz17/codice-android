package una.codice.feature.notes.presentation

import androidx.compose.ui.graphics.Color
import java.util.Calendar
import una.codice.core.designsystem.component.CourseOption
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.ModuleCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.model.countsAsCovered

data class ModuleCoverageItem(
    val number: Int,
    val title: String,
    val percentage: Double,
    val color: Color
)

/** Un repaso pendiente. Queda vacío hasta que las pantallas de repaso lean `/repasos/pendientes`. */
data class ReviewTaskItem(
    val subtopicCode: String,
    val subtopicTitle: String,
    val scheduleText: String,
    val accentColor: Color
)

data class CapturedNotePreview(
    val id: String,
    val courseId: String,
    val title: String,
    val aiTopic: String,
    val transcribedText: String,
    val timestamp: String,
    val sourceType: String
)

/**
 * Un bloque de la transcripción en revisión. [isLowConfidence] sale del umbral del dominio
 * (`TranscriptionBlock.requiresReview`, confianza bajo 0.70): un bloque dudoso cuenta como revisado cuando el estudiante
 * lo confirma o lo corrige ([isConfirmed]), y hasta entonces el apunte no se puede guardar ni auditar.
 */
data class DraftBlock(
    val text: String,
    val confidence: Float,
    val isLowConfidence: Boolean,
    val isConfirmed: Boolean = false,
) {
    /** La IA dudó de este bloque y el estudiante todavía no lo ha visto. */
    val needsReview: Boolean
        get() = isLowConfidence && !isConfirmed
}

private const val UNCLASSIFIED = "Sin clasificar (Temario pendiente)"

data class StudentCaptureUiState(
    val studentName: String = "",
    val weekNumber: Int = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR),
    val courses: List<CourseOption> = emptyList(),
    val selectedCourseId: String? = null,
    val capturedNotes: List<CapturedNotePreview> = emptyList(),

    /** La cobertura real del curso elegido: una fila por subtema del temario que publicó el profesor. */
    val coverage: List<SubtopicCoverage> = emptyList(),

    // Flow states
    val isSourcePickerOpen: Boolean = false,
    val isCameraActive: Boolean = false,
    val isFilePickerOpen: Boolean = false,
    val isEditModalOpen: Boolean = false,
    val draftSourceType: String = "camera",
    val draftSubtopic: String = "Sin clasificar",
    val draftText: String = "",

    /** Id del borrador: el mismo en cada reintento de guardado, para que el servidor no cree el apunte dos veces. */
    val draftId: String = "",

    /**
     * La transcripción por bloques, tal como el estudiante la revisa. Vacía cuando el borrador no viene de la IA y se
     * edita como un solo texto ([draftText]); con bloques, [draftText] es siempre el texto de todos ellos.
     */
    val draftBlocks: List<DraftBlock> = emptyList(),

    /** La IA del servidor está transcribiendo la foto o las páginas; puede tardar unos segundos. */
    val isTranscribing: Boolean = false,

    val isSaving: Boolean = false,
    val isAuditing: Boolean = false,

    /** Qué salió mal al procesar el apunte con la IA o al capturarlo; la pantalla lo muestra en un diálogo. */
    val errorMessage: String? = null,

    /** Resultado de la auditoría para mostrar en un diálogo: qué subtemas tocó el apunte y en qué estado quedaron. */
    val infoMessage: String? = null,

    /** Repasos que vienen. Vacío hasta que el repaso espaciado tenga su pantalla. */
    val upcomingReviews: List<ReviewTaskItem> = emptyList(),
) {
    /** Cuántos bloques dudosos de la transcripción faltan por confirmar o corregir; mientras haya, el apunte no se guarda. */
    val draftBlocksToReview: Int
        get() = draftBlocks.count { it.needsReview }

    /** Hay temario publicado cuando el servidor devolvió al menos un subtema para este curso. */
    val isSyllabusAvailable: Boolean
        get() = coverage.isNotEmpty()

    val availableSubtopics: List<String>
        get() = if (coverage.isEmpty()) listOf(UNCLASSIFIED) else coverage.map { it.label() }

    /** El id del subtema que eligió el estudiante en el menú, o `null` si dejó el apunte sin clasificar. */
    fun subtopicIdOf(label: String): String? = coverage.firstOrNull { it.label() == label }?.subtopicId

    val currentCourseNotes: List<CapturedNotePreview>
        get() = capturedNotes.filter { it.courseId == selectedCourseId }

    val hasNotes: Boolean
        get() = currentCourseNotes.isNotEmpty()

    val totalSubtopicsCount: Int
        get() = coverage.size

    val coveredSubtopicsCount: Int
        get() = coverage.count { it.status.countsAsCovered }

    val globalCoverage: Double
        get() = if (coverage.isEmpty()) 0.0 else coveredSubtopicsCount.toDouble() / coverage.size * 100.0

    /** Subtemas que todavía no están cubiertos (sin apuntes o con cobertura parcial). */
    val openVoidsCount: Int
        get() = totalSubtopicsCount - coveredSubtopicsCount

    val reinforcedCount: Int
        get() = coverage.count { it.status == CoverageStatus.REINFORCED }

    val modules: List<ModuleCoverageItem>
        get() = coverage.groupBy { it.moduleNumber }
            .toSortedMap()
            .map { (number, rows) ->
                val module = ModuleCoverage(number, rows.first().moduleTitle, rows)
                ModuleCoverageItem(
                    number = number,
                    title = module.moduleTitle,
                    percentage = module.coveragePercentage,
                    color = when {
                        module.coveragePercentage >= 80 -> CodiceColors.Lime
                        module.coveragePercentage >= 50 -> CodiceColors.Yellow
                        else -> CodiceColors.Orange
                    }
                )
            }
}

/** El texto con el que el menú del borrador nombra al subtema, por ejemplo `4.3 Manejo de errores`. */
private fun SubtopicCoverage.label(): String = "$subtopicCode $subtopicTitle"
