package una.codice.feature.coverage.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.glassPanel
import una.codice.core.designsystem.motion.SkeletonCard
import una.codice.core.designsystem.motion.staggeredEntrance
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import una.codice.core.designsystem.component.CourseSelector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import una.codice.core.domain.model.CoverageMap
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.ModuleCoverage
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.designsystem.component.Meter
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.feature.coverage.R
import androidx.compose.ui.res.stringResource
import una.codice.core.designsystem.component.describedAs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/**
 * Punto de entrada de la pantalla de Mapa de cobertura: conecta el
 * [CoverageViewModel] real (inyectado por Hilt) con el composable sin estado
 * [CoverageScreen]. Mismo criterio que `AuthRoute`/`AuthScreen` en
 * `:feature:auth` — el composable de pantalla nunca llama al repositorio ni
 * conoce que existe un ViewModel detrás.
 */
@Composable
fun CoverageRoute(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: CoverageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val courseBar by viewModel.courseBar.collectAsStateWithLifecycle()

    CoverageScreen(
        state = state,
        onRetry = viewModel::onRetry,
        modifier = modifier,
        courseBar = courseBar,
        onCourseSelected = viewModel::onCourseSelected,
        onBack = onBack,
    )
}

/**
 * La pantalla de Mapa de cobertura: la lista de subtemas con su estado
 * (issue #20) más el resumen global y las barras de progreso por módulo
 * (issue #21), como el primer `item {}` del mismo `LazyColumn` — tal como
 * quedó previsto al construir #20, sin reestructurar nada.
 *
 * Sin estado propio: todo sale de [state], igual que `AuthScreen`. Cubre los
 * cuatro estados que pide `convenciones.md` — acá son cinco porque
 * [CoverageUiState] separa "vacío" (sin temario) de "sin conexión/error"
 * (ver el KDoc de esa clase para el porqué de la distinción).
 *
 * ### Por qué el punto + la etiqueta, y no solo el punto de color
 *
 * Es un requisito de accesibilidad explícito de `convenciones.md`
 * ("el estado se comunica con color y etiqueta, nunca solo con color").
 * [StatusPill] pinta el punto en el color de Figma (nodo `2:75`) PERO el
 * texto de la etiqueta ("Cubierto", "Parcial", ...) siempre queda en
 * [CoverageColors.TextHigh] — exactamente como en esa sección de Figma, el
 * color nunca es la única señal.
 */
@Composable
fun CoverageScreen(
    state: CoverageUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    courseBar: CourseBarState = CourseBarState(),
    onCourseSelected: (String?) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    // Hallazgo H10.2 de la evaluación heurística (docs/usabilidad/informes):
    // nada en pantalla explicaba qué significa cada estado ("Sin apuntes",
    // "Parcial", "Cubierto", "Reforzado"). Estado local de presentación, no
    // del ViewModel — igual que el diálogo de confirmación de logout, no hay
    // ninguna decisión de negocio en "¿mostrar la ayuda o no?".
    var mostrarAyuda by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // Se llega desde "Cursos": la flecha regresa ahí. Sin `onBack` (vista previa) no se pinta.
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 14.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cov_back),
                    tint = CoverageColors.TextHigh,
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = if (onBack != null) 0.dp else 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                GradientText(text = stringResource(R.string.cov_coverage_map), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.cov_every_subtopic_of_the_course_with_its_),
                    color = CoverageColors.TextMid,
                    fontSize = 14.sp,
                )
            }
            IconButton(
                onClick = { mostrarAyuda = true },
                modifier = Modifier.describedAs(stringResource(R.string.cov_what_does_each_coverage_status_mean)),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(CoverageColors.Glass, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "?",
                        color = CoverageColors.TextMid,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        CourseSelector(
            options = courseBar.options,
            selectedId = courseBar.selectedGroupId,
            onSelect = onCourseSelected,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        when (state) {
            is CoverageUiState.Loading -> LoadingContent()
            is CoverageUiState.Empty -> EmptyContent()
            is CoverageUiState.Error -> ErrorContent(message = state.message, onRetry = onRetry)
            is CoverageUiState.Content -> SubtopicList(coverageMap = state.coverageMap)
        }
    }

    if (mostrarAyuda) {
        CoverageHelpDialog(onDismiss = { mostrarAyuda = false })
    }
}

/** Explica los 4 [CoverageStatus] con el mismo color+etiqueta que usa [StatusPill] en la lista, para que la ayuda se reconozca de un vistazo. */
@Composable
private fun CoverageHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CoverageColors.Glass,
        title = { Text(stringResource(R.string.cov_what_does_each_status_mean), color = CoverageColors.TextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CoverageStatus.entries.forEach { status ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(status.color(), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(status.label(), color = CoverageColors.TextHigh, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(status.helpText(), color = CoverageColors.TextMid, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cov_got_it), color = CoverageColors.Lime, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** Descripción breve de cada estado, para [CoverageHelpDialog]. */
@Composable
private fun CoverageStatus.helpText(): String = when (this) {
    CoverageStatus.NO_NOTES -> stringResource(R.string.cov_you_don_t_have_any_notes_covering_this)
    CoverageStatus.PARTIAL -> stringResource(R.string.cov_you_have_notes_but_they_don_t_cover_th)
    CoverageStatus.COVERED -> stringResource(R.string.cov_your_notes_fully_cover_this_subtopic)
    CoverageStatus.REINFORCED -> stringResource(R.string.cov_covered_and_reinforced_with_more_than_)
}

@Composable
private fun LoadingContent() {
    // Esqueletos con brillo en lugar de una rueda: muestran la forma de lo que va a llegar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 4.dp)
            .describedAs(stringResource(R.string.cov_loading_coverage_map), mergeDescendants = true),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonCard(lines = 3)
        SkeletonCard(lines = 2)
        SkeletonCard(lines = 2)
    }
}

@Composable
private fun EmptyContent() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.cov_no_syllabus_published_yet),
                color = CoverageColors.TextHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cov_your_teacher_hasn_t_published_this_cou),
                color = CoverageColors.TextMid,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = CoverageColors.TextHigh,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoverageColors.Glass,
                    contentColor = CoverageColors.TextHigh,
                ),
            ) {
                Text(text = stringResource(R.string.cov_retry), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * La lista del issue #20 más el resumen del #21 como primer `item {}`: un
 * `LazyColumn` porque el temario completo de un curso puede tener bastantes
 * más subtemas que los que caben en una pantalla — a diferencia de
 * `AuthScreen` (un formulario corto con `verticalScroll`), aquí sí hace
 * falta reciclar filas fuera de pantalla.
 */
@Composable
private fun SubtopicList(coverageMap: CoverageMap) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Box(Modifier.staggeredEntrance(0)) { SummarySection(coverageMap) } }
        itemsIndexed(coverageMap.modules, key = { _, module -> module.moduleNumber }) { index, module ->
            Box(Modifier.staggeredEntrance(index + 1)) { ModuleSection(module) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * El resumen global del issue #21: el porcentaje agregado
 * ([CoverageMap.globalPercentage], ponderado por subtema — ver su KDoc en
 * `:core:domain`) y una barra de progreso por módulo.
 *
 * ### Por qué el número siempre lleva la barra al lado, nunca solo el texto
 *
 * Mismo criterio de accesibilidad que [StatusPill]: un `"62%"` sin nada más
 * ya es una etiqueta de texto, así que aquí la barra no es la que aporta la
 * etiqueta — aporta una lectura rápida por longitud, y el número exacto
 * quita cualquier ambigüedad de cuánto mide esa longitud.
 */
@Composable
private fun SummarySection(coverageMap: CoverageMap) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(20.dp)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            // La cifra protagonista del mapa lleva el degradado del rol.
            GradientText(
                text = coverageMap.globalPercentage.asPercentText(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.cov_overall_coverage),
                color = CoverageColors.TextMid,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Meter(
            progress = (coverageMap.globalPercentage / 100.0).toFloat(),
            color = getCoverageColor(coverageMap.globalPercentage),
            showValue = false,
        )

        if (coverageMap.modules.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                coverageMap.modules.forEach { module -> ModuleProgressRow(module) }
            }
        }
    }
}

@Composable
private fun ModuleProgressRow(module: ModuleCoverage) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.cov_module, module.moduleNumber, module.moduleTitle),
                color = CoverageColors.TextMid,
                fontSize = 13.sp,
            )
            Text(
                text = module.coveragePercentage.asPercentText(),
                color = CoverageColors.TextHigh,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Meter(
            progress = (module.coveragePercentage / 100.0).toFloat(),
            color = getCoverageColor(module.coveragePercentage),
            showValue = false,
        )
    }
}

private fun getCoverageColor(percentage: Double): Color {
    return when {
        percentage >= 80 -> CodiceColors.Lime
        percentage >= 50 -> CodiceColors.Yellow
        percentage >= 25 -> CodiceColors.Orange
        else -> CodiceColors.Orange
    }
}

/** `62.0` -> `"62%"`. Redondeado: un decimal no aporta nada a esta escala y solo ensucia la lectura rápida. */
private fun Double.asPercentText(): String = "${roundToInt()}%"

@Composable
private fun ModuleSection(module: ModuleCoverage) {
    Column {
        Text(
            text = stringResource(R.string.cov_module, module.moduleNumber, module.moduleTitle),
            color = CoverageColors.TextLow,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(18.dp),
        ) {
            module.subtopics.forEachIndexed { index, subtopic ->
                SubtopicRow(subtopic)
                if (index != module.subtopics.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CoverageColors.Canvas.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtopicRow(subtopic: SubtopicCoverage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subtopic.subtopicCode,
                color = CoverageColors.TextLow,
                fontSize = 11.sp,
            )
            Text(
                text = subtopic.subtopicTitle,
                color = CoverageColors.TextHigh,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(12.dp))
        StatusPill(status = subtopic.status)
    }
}

/** El punto de color + la etiqueta de texto — ver el KDoc de [CoverageScreen] para el porqué de las dos cosas juntas. */
@Composable
private fun StatusPill(status: CoverageStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(status.color(), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = status.label(),
            color = CoverageColors.TextHigh,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Colores tal cual Figma (nodo `2:75`) — ver [CoverageColors]. */
private fun CoverageStatus.color(): Color = when (this) {
    CoverageStatus.NO_NOTES -> CoverageColors.Orange
    CoverageStatus.PARTIAL -> CoverageColors.Yellow
    CoverageStatus.COVERED -> CoverageColors.Lime
    CoverageStatus.REINFORCED -> CoverageColors.Blue
}

/** Etiquetas tal cual Figma (nodo `2:75`) — el mismo texto que usa esa sección para cada estado. */
@Composable
private fun CoverageStatus.label(): String = when (this) {
    CoverageStatus.NO_NOTES -> stringResource(R.string.cov_no_notes)
    CoverageStatus.PARTIAL -> stringResource(R.string.cov_partial)
    CoverageStatus.COVERED -> stringResource(R.string.cov_covered)
    CoverageStatus.REINFORCED -> stringResource(R.string.cov_reinforced)
}

private val previewCoverageMap = CoverageMap(
    modules = listOf(
        ModuleCoverage(
            moduleNumber = 1,
            moduleTitle = "Fundamentos",
            subtopics = listOf(
                SubtopicCoverage("1", "1.1", "Introducción a Kotlin", 1, "Fundamentos", CoverageStatus.COVERED),
                SubtopicCoverage("2", "1.2", "Corrutinas", 1, "Fundamentos", CoverageStatus.PARTIAL),
            ),
        ),
        ModuleCoverage(
            moduleNumber = 4,
            moduleTitle = "Programación distribuida",
            subtopics = listOf(
                SubtopicCoverage("3", "4.4", "Servicios REST", 4, "Programación distribuida", CoverageStatus.NO_NOTES),
                SubtopicCoverage("4", "4.5", "Autenticación", 4, "Programación distribuida", CoverageStatus.REINFORCED),
            ),
        ),
    ),
)

@Preview(name = "Con datos", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun CoverageScreenContentPreview() {
    MaterialTheme {
        CoverageScreen(state = CoverageUiState.Content(previewCoverageMap), onRetry = {})
    }
}

@Preview(name = "Cargando", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun CoverageScreenLoadingPreview() {
    MaterialTheme {
        CoverageScreen(state = CoverageUiState.Loading, onRetry = {})
    }
}

@Preview(name = "Sin temario", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun CoverageScreenEmptyPreview() {
    MaterialTheme {
        CoverageScreen(state = CoverageUiState.Empty, onRetry = {})
    }
}

@Preview(name = "Error", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun CoverageScreenErrorPreview() {
    MaterialTheme {
        CoverageScreen(
            state = CoverageUiState.Error("No se pudo conectar. Revisa tu conexión e intenta de nuevo"),
            onRetry = {},
        )
    }
}
