package una.codice.core.domain.model

import java.time.LocalDate

/**
 * Una pregunta de opción múltiple de un repaso. [correctIndex] es la posición de la opción correcta dentro de
 * [options]; la app compara la respuesta del estudiante con ella y la [explanation] se muestra al comprobar.
 */
data class QuizQuestion(
    val statement: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
) {
    fun isCorrect(chosenIndex: Int): Boolean = chosenIndex == correctIndex
}

/** De dónde sale un repaso: el apunte que la auditoría revisó y lo que vio que faltaba en él. */
data class ReviewOrigin(
    val noteId: String?,
    val justification: String?,
)

/**
 * Un repaso que ya toca hacer: la pregunta sobre un subtema con vacío de cobertura y el intervalo de repetición
 * espaciada en el que va (1, 3, 7, 14 o 30 días).
 */
data class PendingReview(
    val id: String,
    val subtopicId: String,
    val subtopicCode: String,
    val subtopicTitle: String,
    val intervalDay: Int,
    val nextDate: LocalDate,
    val questions: List<QuizQuestion>,
    val origin: ReviewOrigin?,
) {
    /**
     * El resultado del repaso a partir de lo que respondió el estudiante, una opción por pregunta y en orden:
     * aprueba solo si acertó todas, y una pregunta sin responder cuenta como fallada.
     */
    fun outcomeFor(chosenOptions: List<Int>): ReviewOutcome {
        val allCorrect = questions.indices.all { index ->
            chosenOptions.getOrNull(index)?.let(questions[index]::isCorrect) == true
        }
        return if (allCorrect) ReviewOutcome.PASSED else ReviewOutcome.FAILED
    }
}

/** Cómo terminó un repaso. Aprobar sube al siguiente intervalo; fallar vuelve a empezar en 1 día. */
enum class ReviewOutcome { PASSED, FAILED }

/** Cuándo vuelve a tocar un repaso después de registrar su resultado. */
data class ReviewSchedule(
    val intervalDay: Int,
    val nextDate: LocalDate,
)

/** Lo que pasó al terminar un repaso: cómo salió y cuándo vuelve a tocar. */
data class ReviewResolution(
    val outcome: ReviewOutcome,
    val schedule: ReviewSchedule,
)
