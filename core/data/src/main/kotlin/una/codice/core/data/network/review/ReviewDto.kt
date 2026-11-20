package una.codice.core.data.network.review

import java.time.LocalDate
import kotlinx.serialization.Serializable
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.QuizQuestion
import una.codice.core.domain.model.ReviewOrigin
import una.codice.core.domain.model.ReviewSchedule

/** `PendingReviewDTO` del backend: un repaso vencido con sus preguntas. Los ids son numéricos en el backend. */
@Serializable
data class PendingReviewDto(
    val id: Long,
    val subtopicId: Long,
    val subtopicCode: String? = null,
    val subtopicTitle: String? = null,
    val intervalDay: Int = 1,
    /** Fecha ISO-8601 sin hora, por ejemplo `2026-10-29`. */
    val nextDate: String,
    val result: String? = null,
    val questions: List<QuizQuestionDto> = emptyList(),
    val origin: QuizOriginDto? = null,
)

/** `QuizQuestionDTO`: `correcta` es el índice de la opción correcta dentro de `opciones`. */
@Serializable
data class QuizQuestionDto(
    val enunciado: String = "",
    val opciones: List<String> = emptyList(),
    val correcta: Int = -1,
    val explicacion: String = "",
)

/** `QuizOriginDTO`: el apunte que la auditoría revisó y lo que vio que faltaba en él. */
@Serializable
data class QuizOriginDto(
    val noteId: Long? = null,
    val justification: String? = null,
    val detectedAt: String? = null,
)

/** `ReviewResultDTO`: el cuerpo de registrar el resultado, `PASSED` o `FAILED`. */
@Serializable
data class ReviewResultRequestDto(
    val result: String,
)

/** De la respuesta de registrar un resultado (`ReviewDTO`) la app solo necesita cuándo vuelve a tocar el repaso. */
@Serializable
data class ReviewScheduleDto(
    val id: Long,
    val intervalDay: Int,
    val nextDate: String,
    val result: String? = null,
)

fun PendingReviewDto.toDomain(): PendingReview = PendingReview(
    id = id.toString(),
    subtopicId = subtopicId.toString(),
    subtopicCode = subtopicCode.orEmpty(),
    subtopicTitle = subtopicTitle.orEmpty(),
    intervalDay = intervalDay,
    nextDate = LocalDate.parse(nextDate),
    questions = questions.map { it.toDomain() },
    origin = origin?.let { ReviewOrigin(noteId = it.noteId?.toString(), justification = it.justification) },
)

fun QuizQuestionDto.toDomain(): QuizQuestion = QuizQuestion(
    statement = enunciado,
    options = opciones,
    correctIndex = correcta,
    explanation = explicacion,
)

fun ReviewScheduleDto.toDomain(): ReviewSchedule = ReviewSchedule(
    intervalDay = intervalDay,
    nextDate = LocalDate.parse(nextDate),
)
