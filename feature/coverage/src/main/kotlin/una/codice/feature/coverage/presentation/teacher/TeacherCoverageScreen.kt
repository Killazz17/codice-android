package una.codice.feature.coverage.presentation.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import una.codice.core.designsystem.component.EmptyState
import una.codice.core.designsystem.component.ErrorState
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.component.Meter
import una.codice.core.designsystem.component.CodiceLoader
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.GroupModuleCoverage
import una.codice.core.domain.model.GroupSubtopicCoverage
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.TeacherGroupSummary
import kotlin.math.roundToInt
import una.codice.feature.coverage.R
import androidx.compose.ui.res.stringResource

/**
 * "Cobertura del grupo" (D7). Agregada y anónima por diseño: por subtema, qué parte del grupo lo
 * tiene cubierto, parcial o sin apuntes. Nunca aparece el nombre de un estudiante.
 */
@Composable
fun TeacherCoverageRoute(
    modifier: Modifier = Modifier,
    viewModel: TeacherCoverageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TeacherCoverageScreen(
        state = state,
        onGroupSelected = viewModel::onGroupSelected,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun TeacherCoverageScreen(
    state: TeacherCoverageUiState,
    onGroupSelected: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        when (state) {
            TeacherCoverageUiState.Loading -> CodiceLoader(modifier = Modifier.align(Alignment.Center))
            TeacherCoverageUiState.NoGroups -> EmptyState(
                title = stringResource(R.string.cov_no_groups_yet),
                description = stringResource(R.string.cov_create_a_course_in_the_courses_tab_to_),
                modifier = Modifier.align(Alignment.Center),
            )
            is TeacherCoverageUiState.Error -> ErrorState(
                message = state.message,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
            is TeacherCoverageUiState.Content -> CoverageContent(state, onGroupSelected)
        }
    }
}

@Composable
private fun CoverageContent(state: TeacherCoverageUiState.Content, onGroupSelected: (String) -> Unit) {
    val coverage = state.coverage
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 8.dp)) {
                GradientText(text = stringResource(R.string.cov_group_coverage), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.cov_aggregated_and_anonymous_never_shown_p),
                    color = CodiceColors.TextMid,
                    fontSize = 14.sp,
                )
            }
        }
        if (state.groups.size > 1) {
            item { GroupChips(state.groups, state.selectedGroupId, onGroupSelected) }
        }
        item { SummaryCard(coverage) }
        if (coverage.modules.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.cov_the_syllabus_is_empty),
                    description = stringResource(R.string.cov_once_the_course_has_subtopics_you_ll_s),
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        coverage.modules.forEach { module ->
            item(key = "m-${module.moduleNumber}-${module.moduleTitle}") { ModuleHeader(module) }
            items(module.subtopics, key = { it.subtopicId }) { SubtopicRow(it) }
        }
        item { Legend() }
    }
}

@Composable
private fun GroupChips(groups: List<TeacherGroupSummary>, selectedId: String, onSelected: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(groups, key = { it.group.id }) { summary ->
            val selected = summary.group.id == selectedId
            val accent = LocalAccent.current
            val code = summary.group.courseCode.ifBlank { summary.group.courseId }
            Text(
                text = stringResource(R.string.cov_g, code, summary.group.groupNumber),
                color = if (selected) CodiceColors.Canvas else CodiceColors.TextMid,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    // Seleccionado: el degradado del rol; sin seleccionar: cristal.
                    .then(
                        if (selected) Modifier.background(accent.brush())
                        else Modifier.background(Color.White.copy(alpha = 0.06f))
                    )
                    .clickable { onSelected(summary.group.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SummaryCard(coverage: GroupCoverage) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        // La cifra protagonista de la pantalla: borde neón y número con el degradado del rol.
        glow = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                GradientText(
                    text = "${coverage.averageCoveredPercentage.roundToInt()}%",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cov_covered_on_average_students, coverage.totalStudents),
                    color = CodiceColors.TextMid,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            coverage.modules.forEach { module ->
                Meter(
                    progress = (module.averageCoveredPercentage / 100).toFloat(),
                    label = stringResource(R.string.cov_module, module.moduleNumber, module.moduleTitle),
                    color = colorFor(module.averageCoveredPercentage),
                )
            }
        }
    }
}

@Composable
private fun ModuleHeader(module: GroupModuleCoverage) {
    Text(
        text = stringResource(R.string.cov_module, module.moduleNumber, module.moduleTitle),
        color = CodiceColors.TextLow,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    )
}

@Composable
private fun SubtopicRow(subtopic: GroupSubtopicCoverage) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subtopic.subtopicCode, color = CodiceColors.TextLow, fontSize = 11.sp)
                    Text(subtopic.subtopicTitle, color = CodiceColors.TextHigh, fontWeight = FontWeight.Bold)
                }
                // El número es solo la parte cubierta; sin la etiqueta, un subtema 100 % parcial se leía como "0%" con la
                // barra entera en amarillo. El parcial va aparte para que el reparto completo se entienda sin la barra.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.cov_covered_2, subtopic.coveredPercentage.roundToInt()),
                        color = colorFor(subtopic.coveredPercentage),
                        fontWeight = FontWeight.Bold,
                    )
                    if (subtopic.partialPercentage > 0) {
                        Text(
                            text = stringResource(R.string.cov_partial_2, subtopic.partialPercentage.roundToInt()),
                            color = CodiceColors.Yellow,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            StackedBar(subtopic)
        }
    }
}

/** Barra con las tres partes del grupo: cubierto, parcial y sin apuntes (suman 100 %). */
@Composable
private fun StackedBar(subtopic: GroupSubtopicCoverage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(CodiceColors.GlassHigh),
    ) {
        Segment(subtopic.coveredPercentage, CodiceColors.Lime)
        Segment(subtopic.partialPercentage, CodiceColors.Yellow)
        Segment(subtopic.noNotesPercentage, CodiceColors.Orange.copy(alpha = 0.6f))
    }
}

@Composable
private fun RowScope.Segment(percentage: Double, color: Color) {
    if (percentage > 0) {
        Box(
            Modifier
                .weight(percentage.toFloat())
                .fillMaxSize()
                .background(color),
        )
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LegendItem(stringResource(R.string.cov_covered), CodiceColors.Lime)
        LegendItem(stringResource(R.string.cov_partial), CodiceColors.Yellow)
        LegendItem(stringResource(R.string.cov_no_notes), CodiceColors.Orange)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = CodiceColors.TextMid, fontSize = 12.sp)
    }
}

private fun colorFor(percentage: Double): Color = when {
    percentage >= 70 -> CodiceColors.Lime
    percentage >= 40 -> CodiceColors.Yellow
    else -> CodiceColors.Orange
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun TeacherCoverageScreenPreview() {
    val group = StudentGroup("20", "10", "Móviles", "01", "2026-II", "Laura Jiménez", courseCode = "EIF411")
    TeacherCoverageScreen(
        state = TeacherCoverageUiState.Content(
            groups = listOf(TeacherGroupSummary(group, 2, 0.5f, 0)),
            selectedGroupId = "20",
            coverage = GroupCoverage(
                totalStudents = 2,
                modules = listOf(
                    GroupModuleCoverage(
                        4, "Árboles",
                        listOf(
                            GroupSubtopicCoverage("1", "4.1", "Árboles AVL", 50.0, 50.0, 0.0),
                            GroupSubtopicCoverage("2", "4.2", "Heaps", 0.0, 0.0, 100.0),
                        ),
                    ),
                ),
            ),
        ),
        onGroupSelected = {},
        onRetry = {},
    )
}
