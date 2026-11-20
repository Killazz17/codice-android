package una.codice.feature.review.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.EmptyState
import una.codice.core.designsystem.component.ErrorState
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.Meter
import una.codice.core.designsystem.component.Pill
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import una.codice.core.designsystem.motion.CodiceMotion
import una.codice.core.designsystem.motion.ConfettiBurst
import una.codice.core.designsystem.motion.SkeletonCard
import una.codice.core.designsystem.motion.rememberMotionEnabled
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.QuizQuestion
import una.codice.core.domain.model.ReviewOrigin
import una.codice.feature.review.R
import androidx.compose.ui.res.stringResource
import una.codice.core.designsystem.component.describedAs
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color

/** Conecta [ReviewScreen] con su [ReviewViewModel]: la pantalla en sí no conoce ni a Hilt ni al ViewModel. */
@Composable
fun ReviewRoute(
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ReviewScreen(
        state = state,
        onRetry = viewModel::onRetry,
        onGenerate = viewModel::onGenerate,
        onPractice = viewModel::onPractice,
        onSelectOption = viewModel::onSelectOption,
        onCheck = viewModel::onCheck,
        onContinue = viewModel::onContinue,
        onFinish = viewModel::onFinish,
        modifier = modifier,
    )
}

/**
 * La pantalla de Repaso: el quiz con repetición espaciada. Sin estado propio, todo sale de [state]; cada estado
 * ([ReviewUiState]) tiene su propio contenido, incluidos los de carga, vacío y error.
 */
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onGenerate: () -> Unit = {},
    onPractice: () -> Unit = {},
    onSelectOption: (Int) -> Unit = {},
    onCheck: () -> Unit = {},
    onContinue: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(modifier = Modifier.padding(horizontal = 26.dp, vertical = 20.dp)) {
            GradientText(text = stringResource(R.string.rev_review), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.rev_questions_about_the_gaps_in_your_cover),
                color = CodiceColors.TextMid,
                fontSize = 14.sp,
            )
        }

        when (state) {
            is ReviewUiState.Loading -> Column(
                modifier = Modifier
                    .padding(horizontal = 26.dp)
                    .describedAs(stringResource(R.string.rev_loading_reviews), mergeDescendants = true),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonCard(lines = 3)
                SkeletonCard(lines = 2)
            }
            is ReviewUiState.Empty -> EmptyContent(state, onGenerate, onPractice)
            is ReviewUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = state.message, onRetry = onRetry)
            }
            is ReviewUiState.Quiz -> QuizContent(state, onSelectOption, onCheck, onContinue)
            is ReviewUiState.Finished -> FinishedContent(state, onFinish)
        }
    }
}

@Composable
private fun EmptyContent(state: ReviewUiState.Empty, onGenerate: () -> Unit, onPractice: () -> Unit) {
    val description = state.notice ?: if (state.upcoming.isEmpty()) {
        stringResource(R.string.rev_generate_questions_from_the_gaps_in_yo)
    } else {
        stringResource(R.string.rev_your_scheduled_reviews_are_due_on_thei)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(title = stringResource(R.string.rev_you_have_no_reviews_for_today), description = description)
        if (state.upcoming.isNotEmpty()) UpcomingCard(state.upcoming, onPractice)
        CtaButton(text = stringResource(R.string.rev_generate_reviews), onClick = onGenerate, isLoading = state.isGenerating)
    }
}

/** Los repasos ya programados y el botón para practicarlos ahora: practicar no cambia cuándo toca cada uno. */
@Composable
private fun UpcomingCard(upcoming: List<PendingReview>, onPractice: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.rev_upcoming_reviews, upcoming.size),
                color = CodiceColors.TextLow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            upcoming.forEach { review ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${review.subtopicCode} ${review.subtopicTitle}".trim(),
                        color = CodiceColors.TextHigh,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = stringResource(R.string.rev_due, shortDate(review.nextDate)), color = CodiceColors.TextMid, fontSize = 12.sp)
                }
            }
            Text(
                text = stringResource(R.string.rev_practice_doesn_t_change_your_schedule_),
                color = CodiceColors.TextLow,
                fontSize = 12.sp,
            )
            CtaButton(text = stringResource(R.string.rev_practice_now), onClick = onPractice)
        }
    }
}

@Composable
private fun QuizContent(
    state: ReviewUiState.Quiz,
    onSelectOption: (Int) -> Unit,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
) {
    val review = state.review
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Meter(
            progress = (state.position - 1).toFloat() / state.total,
            label = stringResource(R.string.rev_review_of, state.position, state.total),
            // Un "0 %" junto a "Repaso 1 de 1" no significaba nada para quien lo veía: queda solo la barra y el texto.
            showValue = false,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                text = "${review.subtopicCode} ${review.subtopicTitle}".trim(),
                color = CodiceColors.Blue,
                modifier = Modifier.weight(1f, fill = false),
            )
            Pill(text = stringResource(R.string.rev_review_2, days(review.intervalDay)), color = CodiceColors.Yellow)
        }

        // Una línea que explica qué es esta pantalla: antes la persona caía directo en una pregunta, sin contexto.
        Text(
            text = stringResource(if (state.isPractice) R.string.rev_practice_doesn_t_change_your_review_sc else R.string.rev_quiz_hint),
            color = CodiceColors.TextMid,
            fontSize = 12.sp,
        )

        if (review.questions.size > 1) {
            Text(
                text = stringResource(R.string.rev_question_of, state.questionIndex + 1, review.questions.size),
                color = CodiceColors.TextLow,
                fontSize = 12.sp,
            )
        }

        // La pregunta es la protagonista de la pantalla: lleva el borde de luz neón.
        GlassCard(modifier = Modifier.fillMaxWidth(), glow = true) {
            Text(
                text = state.question.statement,
                color = CodiceColors.TextHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        state.question.options.forEachIndexed { index, option ->
            OptionRow(index = index, text = option, state = state, onClick = { onSelectOption(index) })
        }

        if (state.isChecked) ExplanationCard(state)

        state.saveError?.let { message ->
            Text(text = message, color = CodiceColors.Orange, fontSize = 13.sp)
        }

        CtaButton(
            text = actionLabel(state),
            onClick = if (state.isChecked) onContinue else onCheck,
            enabled = state.isChecked || state.selectedOption != null,
            isLoading = state.isSaving,
        )
    }
}

/** Una opción de respuesta. Tras comprobar, el color va acompañado de un ícono: el color solo no basta. */
@Composable
private fun OptionRow(index: Int, text: String, state: ReviewUiState.Quiz, onClick: () -> Unit) {
    val selected = state.selectedOption == index
    val isCorrectOption = state.isChecked && index == state.question.correctIndex
    val isWrongChoice = state.isChecked && selected && !isCorrectOption
    val idle = !selected && !isCorrectOption && !isWrongChoice
    val targetAccent = when {
        isCorrectOption -> CodiceColors.Lime
        isWrongChoice -> CodiceColors.Orange
        selected -> CodiceColors.Blue
        else -> Color.White.copy(alpha = 0.18f)
    }
    // Los colores se funden en vez de saltar: el acierto "se enciende" y el error se tiñe.
    val accent by animateColorAsState(targetAccent, tween(CodiceMotion.StandardMs), label = "option-accent")
    val fill by animateColorAsState(
        targetValue = when {
            isCorrectOption -> CodiceColors.Lime.copy(alpha = 0.12f)
            isWrongChoice -> CodiceColors.Orange.copy(alpha = 0.10f)
            else -> CodiceColors.Glass
        },
        animationSpec = tween(CodiceMotion.StandardMs),
        label = "option-fill",
    )
    val shape = RoundedCornerShape(14.dp)

    val motion = rememberMotionEnabled()
    val pulse = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    // Acierto: la fila crece un poco y se asienta con un rebote. Fallo: se sacude de lado a lado, como un "no".
    LaunchedEffect(isCorrectOption) {
        if (isCorrectOption && motion) {
            pulse.animateTo(1.04f, tween(120))
            pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
        }
    }
    LaunchedEffect(isWrongChoice) {
        if (isWrongChoice && motion) {
            repeat(3) {
                shake.animateTo(8f, tween(45))
                shake.animateTo(-8f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
                translationX = shake.value * density
            }
            .clip(shape)
            .background(fill)
            .border(width = 1.dp, color = accent, shape = shape)
            .selectable(
                selected = selected,
                enabled = !state.isChecked && !state.isSaving,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${'A' + index}",
            color = if (idle) CodiceColors.TextMid else accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = text, color = CodiceColors.TextHigh, fontSize = 15.sp, modifier = Modifier.weight(1f))
        when {
            isCorrectOption -> Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.rev_correct), tint = accent, modifier = Modifier.size(20.dp))
            isWrongChoice -> Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.rev_incorrect), tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ExplanationCard(state: ReviewUiState.Quiz) {
    val color = if (state.isCorrect) CodiceColors.Lime else CodiceColors.Orange
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (state.isCorrect) stringResource(R.string.rev_correct_2) else stringResource(R.string.rev_incorrect_2),
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = state.question.explanation, color = CodiceColors.TextHigh, fontSize = 14.sp)
            state.review.origin?.justification?.takeIf { it.isNotBlank() }?.let { why ->
                Text(text = stringResource(R.string.rev_why_we_asked, why), color = CodiceColors.TextMid, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FinishedContent(state: ReviewUiState.Finished, onFinish: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, end = 26.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (state.isPractice) stringResource(R.string.rev_you_finished_the_practice) else stringResource(R.string.rev_you_finished_today_s_reviews),
                color = CodiceColors.TextHigh,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.rev_passed_to_repeat, state.passedCount, state.failedCount),
                color = CodiceColors.TextMid,
                fontSize = 14.sp,
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.results.forEach { line -> ResultRow(line) }
                }
            }

            CtaButton(text = stringResource(R.string.rev_done), onClick = onFinish)
        }
        // Celebración al terminar: se dispara una vez al entrar a esta pantalla y no intercepta toques.
        ConfettiBurst(trigger = 1, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ResultRow(line: ReviewResultLine) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = line.subtopic, color = CodiceColors.TextHigh, fontSize = 14.sp)
            Text(
                text = line.nextIntervalDay?.let { stringResource(R.string.rev_due_again_in, days(it)) } ?: stringResource(R.string.rev_still_due_on_its_date),
                color = CodiceColors.TextLow,
                fontSize = 12.sp,
            )
        }
        Pill(
            text = if (line.passed) stringResource(R.string.rev_passed) else stringResource(R.string.rev_to_repeat),
            color = if (line.passed) CodiceColors.Lime else CodiceColors.Orange,
        )
    }
}

/** El texto del botón principal según en qué punto de la pregunta va el estudiante. */
@Composable
private fun actionLabel(state: ReviewUiState.Quiz): String = when {
    !state.isChecked -> stringResource(R.string.rev_check)
    state.saveError != null -> stringResource(R.string.rev_retry_saving)
    state.isLastQuestion && state.isLastReview -> stringResource(R.string.rev_finish)
    else -> stringResource(R.string.rev_continue)
}

@Composable
private fun days(count: Int): String =
    if (count == 1) stringResource(R.string.rev_one_day) else stringResource(R.string.rev_n_days, count)

/** `2026-10-29` -> `"29 oct"`. */
/** La fecha corta en el idioma de la app (no el del sistema): `d MMM` da «3 nov» en español y «3 Nov» en inglés. */
@Composable
private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]))

private fun previewQuestion() = QuizQuestion(
    statement = "¿Qué código HTTP devuelve el servidor cuando el token vence?",
    options = listOf("200 OK", "401 Unauthorized", "404 Not Found"),
    correctIndex = 1,
    explanation = "Un token vencido no autentica: el servidor responde 401 y la app pide iniciar sesión otra vez.",
)

private fun previewReview() = PendingReview(
    id = "1",
    subtopicId = "10",
    subtopicCode = "4.3",
    subtopicTitle = "Manejo de errores",
    intervalDay = 3,
    nextDate = LocalDate.of(2026, 10, 29),
    questions = listOf(previewQuestion()),
    origin = ReviewOrigin(noteId = "7", justification = "Tu apunte no menciona los códigos de estado."),
)

private const val PREVIEW_NIGHT = android.content.res.Configuration.UI_MODE_NIGHT_YES

@Preview(name = "Pregunta", uiMode = PREVIEW_NIGHT, showBackground = true)
@Composable
private fun ReviewQuizPreview() {
    MaterialTheme {
        ReviewScreen(state = ReviewUiState.Quiz(review = previewReview(), position = 1, total = 3, selectedOption = 1))
    }
}

@Preview(name = "Respuesta incorrecta", uiMode = PREVIEW_NIGHT, showBackground = true)
@Composable
private fun ReviewQuizWrongPreview() {
    MaterialTheme {
        ReviewScreen(
            state = ReviewUiState.Quiz(review = previewReview(), position = 2, total = 3, selectedOption = 0, isChecked = true),
        )
    }
}

@Preview(name = "Sin repasos", uiMode = PREVIEW_NIGHT, showBackground = true)
@Composable
private fun ReviewEmptyPreview() {
    MaterialTheme { ReviewScreen(state = ReviewUiState.Empty()) }
}

@Preview(name = "Error", uiMode = PREVIEW_NIGHT, showBackground = true)
@Composable
private fun ReviewErrorPreview() {
    MaterialTheme {
        ReviewScreen(state = ReviewUiState.Error("No se pudo conectar. Revisa tu conexión e intenta de nuevo"))
    }
}

@Preview(name = "Terminado", uiMode = PREVIEW_NIGHT, showBackground = true)
@Composable
private fun ReviewFinishedPreview() {
    MaterialTheme {
        ReviewScreen(
            state = ReviewUiState.Finished(
                listOf(
                    ReviewResultLine("4.3 Manejo de errores", passed = true, nextIntervalDay = 3),
                    ReviewResultLine("2.1 Funciones", passed = false, nextIntervalDay = 1),
                ),
            ),
        )
    }
}
