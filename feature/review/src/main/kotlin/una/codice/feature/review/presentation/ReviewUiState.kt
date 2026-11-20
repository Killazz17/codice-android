package una.codice.feature.review.presentation

import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.QuizQuestion

/**
 * Todo lo que la pantalla de Repaso necesita para dibujarse, un estado a la vez.
 *
 * El recorrido es: [Loading] → [Quiz] (una pregunta tras otra) → [Finished]; sin repasos que tocar hoy se ve [Empty],
 * y un fallo al pedirlos se ve como [Error] con salida para reintentar.
 */
sealed interface ReviewUiState {

    /** Pidiendo al servidor los repasos que tocan hoy. */
    data object Loading : ReviewUiState

    /**
     * No hay repasos que toquen hoy. Desde aquí el estudiante puede pedir que la IA genere repasos a partir de sus
     * vacíos de cobertura; [notice] cuenta cómo salió (los repasos nuevos empiezan a tocar al día siguiente).
     *
     * @property upcoming Los repasos ya generados que todavía no vencen. El estudiante puede practicarlos ahora, sin
     *   cambiar su calendario.
     */
    data class Empty(
        val notice: String? = null,
        val isGenerating: Boolean = false,
        val upcoming: List<PendingReview> = emptyList(),
    ) : ReviewUiState

    /** No se pudieron pedir los repasos. */
    data class Error(val message: String) : ReviewUiState

    /**
     * Una pregunta del repaso en curso: primero se elige una opción, luego se comprueba (aparece la explicación) y
     * después se continúa. Al terminar las preguntas de un repaso se guarda su resultado en el servidor.
     *
     * @property position Qué repaso de la tanda es este, desde 1.
     * @property total Cuántos repasos tiene la tanda de hoy.
     * @property saveError Por qué no se pudo guardar el resultado; el estudiante puede reintentar sin perder sus respuestas.
     * @property isPractice Es una práctica antes de tiempo: se muestra si acertó, pero nada se guarda en el servidor.
     */
    data class Quiz(
        val review: PendingReview,
        val position: Int,
        val total: Int,
        val questionIndex: Int = 0,
        val selectedOption: Int? = null,
        val isChecked: Boolean = false,
        val isSaving: Boolean = false,
        val saveError: String? = null,
        val isPractice: Boolean = false,
    ) : ReviewUiState {
        val question: QuizQuestion get() = review.questions[questionIndex]

        val isCorrect: Boolean get() = selectedOption?.let(question::isCorrect) == true

        val isLastQuestion: Boolean get() = questionIndex == review.questions.lastIndex

        val isLastReview: Boolean get() = position == total
    }

    /** Se terminó la tanda de hoy (o la práctica): cómo salió cada repaso y, si fue de hoy, cuándo vuelve a tocar. */
    data class Finished(val results: List<ReviewResultLine>, val isPractice: Boolean = false) : ReviewUiState {
        val passedCount: Int get() = results.count { it.passed }

        val failedCount: Int get() = results.size - passedCount
    }
}

/**
 * Un repaso ya resuelto: de qué subtema era, si lo aprobó y en cuántos días vuelve a tocar. [nextIntervalDay] es nulo
 * en una práctica, porque practicar antes de tiempo no reprograma nada.
 */
data class ReviewResultLine(
    val subtopic: String,
    val passed: Boolean,
    val nextIntervalDay: Int?,
)
