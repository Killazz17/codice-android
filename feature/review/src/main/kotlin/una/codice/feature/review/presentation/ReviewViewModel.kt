package una.codice.feature.review.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.review.GenerateReviewsUseCase
import una.codice.core.domain.usecase.review.GetPendingReviewsUseCase
import una.codice.core.domain.usecase.review.GetScheduledReviewsUseCase
import una.codice.core.domain.usecase.review.RecordReviewResultUseCase

/**
 * Orquesta la pantalla de Repaso (issue #42): pide los repasos que tocan hoy, lleva al estudiante pregunta por
 * pregunta y manda el resultado de cada repaso al servidor, que reprograma el siguiente según la repetición espaciada.
 *
 * El servidor identifica al estudiante por su token, así que aquí no hace falta la sesión. La tanda de repasos, el que
 * se resuelve y las respuestas dadas viven en este ViewModel; la pantalla solo dibuja [state].
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val getPendingReviews: GetPendingReviewsUseCase,
    private val getScheduledReviews: GetScheduledReviewsUseCase,
    private val generateReviews: GenerateReviewsUseCase,
    private val recordReviewResult: RecordReviewResultUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private var reviews: List<PendingReview> = emptyList()
    private var current = 0

    // Se está practicando antes de tiempo: el resultado se calcula en la app y no se manda al servidor, así el
    // calendario de repetición espaciada no cambia.
    private var practicing = false

    // La opción elegida en cada pregunta del repaso en curso. Se conserva si guardar el resultado falla, para que
    // reintentar no obligue a responder otra vez.
    private val answers = mutableMapOf<Int, Int>()

    private val results = mutableListOf<ReviewResultLine>()

    init {
        load()
    }

    /** "Reintentar" después de un [ReviewUiState.Error]. */
    fun onRetry() {
        if (_state.value is ReviewUiState.Error) load()
    }

    /** "Generar repasos" desde [ReviewUiState.Empty]: la IA crea preguntas sobre los vacíos de cobertura. */
    fun onGenerate() {
        val state = _state.value as? ReviewUiState.Empty ?: return
        if (state.isGenerating) return

        _state.value = state.copy(isGenerating = true)
        viewModelScope.launch {
            _state.value = when (val result = generateReviews()) {
                is DomainResult.Success -> {
                    // Los repasos nuevos tocan mañana, pero ya se pueden practicar: se piden para ofrecerlos.
                    val upcoming = loadUpcoming()
                    ReviewUiState.Empty(
                        notice = generatedNotice(created = result.data.size, hasUpcoming = upcoming.isNotEmpty()),
                        upcoming = upcoming,
                    )
                }
                is DomainResult.Failure -> ReviewUiState.Empty(notice = result.error.message, upcoming = state.upcoming)
            }
        }
    }

    /** "Practicar ahora" desde [ReviewUiState.Empty]: recorre los repasos que aún no vencen sin tocar el calendario. */
    fun onPractice() {
        val state = _state.value as? ReviewUiState.Empty ?: return
        if (state.isGenerating || state.upcoming.isEmpty()) return

        practicing = true
        startRound(state.upcoming)
    }

    fun onSelectOption(index: Int) {
        val state = _state.value as? ReviewUiState.Quiz ?: return
        if (state.isChecked || state.isSaving || index !in state.question.options.indices) return
        _state.value = state.copy(selectedOption = index)
    }

    /** "Comprobar": revela si la opción era la correcta y la explicación. */
    fun onCheck() {
        val state = _state.value as? ReviewUiState.Quiz ?: return
        if (state.selectedOption == null || state.isChecked) return
        _state.value = state.copy(isChecked = true)
    }

    /**
     * "Continuar" tras comprobar: pasa a la siguiente pregunta del repaso y, si era la última, guarda el resultado en el
     * servidor. Si guardar falla, el mismo botón reintenta con las respuestas ya dadas.
     */
    fun onContinue() {
        val state = _state.value as? ReviewUiState.Quiz ?: return
        val selected = state.selectedOption
        if (!state.isChecked || state.isSaving || selected == null) return

        answers[state.questionIndex] = selected
        if (state.isLastQuestion) {
            saveResult(state)
        } else {
            _state.value = state.copy(
                questionIndex = state.questionIndex + 1,
                selectedOption = null,
                isChecked = false,
                saveError = null,
            )
        }
    }

    /** "Listo" al terminar la tanda: vuelve a pedir los repasos por si quedó alguno más. */
    fun onFinish() {
        if (_state.value is ReviewUiState.Finished) load()
    }

    private fun load() {
        practicing = false
        _state.value = ReviewUiState.Loading
        viewModelScope.launch {
            when (val result = getPendingReviews()) {
                is DomainResult.Success ->
                    if (result.data.isEmpty()) _state.value = ReviewUiState.Empty(upcoming = loadUpcoming()) else startRound(result.data)
                is DomainResult.Failure -> _state.value = ReviewUiState.Error(result.error.message)
            }
        }
    }

    /** Los repasos que aún no vencen. Si pedirlos falla no es un error de la pantalla: solo no se ofrece practicar. */
    private suspend fun loadUpcoming(): List<PendingReview> =
        (getScheduledReviews() as? DomainResult.Success)?.data.orEmpty()

    private fun startRound(round: List<PendingReview>) {
        reviews = round
        current = 0
        answers.clear()
        results.clear()
        showCurrentReview()
    }

    private fun showCurrentReview() {
        _state.value = ReviewUiState.Quiz(
            review = reviews[current],
            position = current + 1,
            total = reviews.size,
            isPractice = practicing,
        )
    }

    // Practicar antes de tiempo no cambia el calendario: se calcula si acertó y no se manda nada al servidor.
    private fun recordPractice(state: ReviewUiState.Quiz) {
        val chosen = state.review.questions.indices.map { answers[it] ?: -1 }
        results += ReviewResultLine(
            subtopic = "${state.review.subtopicCode} ${state.review.subtopicTitle}".trim(),
            passed = state.review.outcomeFor(chosen) == ReviewOutcome.PASSED,
            nextIntervalDay = null,
        )
        showNext()
    }

    private fun saveResult(state: ReviewUiState.Quiz) {
        if (practicing) {
            recordPractice(state)
            return
        }
        _state.value = state.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val chosen = state.review.questions.indices.map { answers[it] ?: -1 }
            when (val result = recordReviewResult(state.review, chosen)) {
                is DomainResult.Success -> {
                    results += ReviewResultLine(
                        subtopic = "${state.review.subtopicCode} ${state.review.subtopicTitle}".trim(),
                        passed = result.data.outcome == ReviewOutcome.PASSED,
                        nextIntervalDay = result.data.schedule.intervalDay,
                    )
                    showNext()
                }
                is DomainResult.Failure -> _state.value = state.copy(isSaving = false, saveError = result.error.message)
            }
        }
    }

    private fun showNext() {
        current++
        answers.clear()
        if (current < reviews.size) {
            showCurrentReview()
        } else {
            _state.value = ReviewUiState.Finished(results.toList(), isPractice = practicing)
        }
    }

    private fun generatedNotice(created: Int, hasUpcoming: Boolean): String = when {
        created == 0 && hasUpcoming ->
            "Ya tienes repasos para todos tus vacíos. Cada uno toca en su fecha y puedes practicarlos ahora."
        created == 0 -> "No hay vacíos de cobertura sin repaso. Captura y audita más apuntes y vuelve a intentarlo."
        created == 1 -> "Se creó 1 repaso. Toca mañana, y puedes practicarlo ahora mismo."
        else -> "Se crearon $created repasos. Tocan mañana, y puedes practicarlos ahora mismo."
    }
}
