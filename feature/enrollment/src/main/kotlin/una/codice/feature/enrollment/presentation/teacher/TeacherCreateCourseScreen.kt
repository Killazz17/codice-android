package una.codice.feature.enrollment.presentation.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.feature.enrollment.R
import androidx.compose.ui.res.stringResource

/**
 * "Crear curso" (D2). Al crearse, [onCreated] recibe el id del grupo para abrir su invitación.
 */
@Composable
fun TeacherCreateCourseRoute(
    onBack: () -> Unit,
    onCreated: (groupId: String) -> Unit,
) {
    val viewModel: TeacherCreateCourseViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.created.collect(onCreated)
    }

    TeacherCreateCourseScreen(
        state = state,
        onBack = onBack,
        onCodeChange = viewModel::onCodeChange,
        onNameChange = viewModel::onNameChange,
        onGroupNumberChange = viewModel::onGroupNumberChange,
        onPeriodChange = viewModel::onPeriodChange,
        onRequiresApprovalChange = viewModel::onRequiresApprovalChange,
        onSubmit = viewModel::onSubmit,
    )
}

@Composable
fun TeacherCreateCourseScreen(
    state: TeacherCreateCourseUiState,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onGroupNumberChange: (String) -> Unit,
    onPeriodChange: (String) -> Unit,
    onRequiresApprovalChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { CreateCourseTopBar(onBack = onBack) },
        bottomBar = {
            Column(Modifier.padding(24.dp)) {
                state.errorMessage?.let {
                    Text(it, color = CodiceColors.Orange, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                CtaButton(
                    text = stringResource(R.string.enr_create_course_and_generate_code),
                    onClick = onSubmit,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(stringResource(R.string.enr_course))
            CourseTextField(
                label = stringResource(R.string.enr_code),
                value = state.code,
                onValueChange = onCodeChange,
                placeholder = "EIF411",
                error = state.fieldErrors[CourseField.CODE],
                capitalization = KeyboardCapitalization.Characters,
            )
            CourseTextField(
                label = stringResource(R.string.enr_name),
                value = state.name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.enr_mobile_platforms_design_and_programmin),
                error = state.fieldErrors[CourseField.NAME],
                capitalization = KeyboardCapitalization.Sentences,
            )

            SectionLabel(stringResource(R.string.enr_first_group))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    CourseTextField(
                        label = stringResource(R.string.enr_group_3),
                        value = state.groupNumber,
                        onValueChange = onGroupNumberChange,
                        placeholder = "01",
                        error = state.fieldErrors[CourseField.GROUP_NUMBER],
                        keyboardType = KeyboardType.Number,
                    )
                }
                Column(Modifier.weight(2f)) {
                    CourseTextField(
                        label = stringResource(R.string.enr_term),
                        value = state.period,
                        onValueChange = onPeriodChange,
                        placeholder = "2026-II",
                        error = state.fieldErrors[CourseField.PERIOD],
                        capitalization = KeyboardCapitalization.Characters,
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.enr_approve_each_request), color = CodiceColors.TextHigh, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (state.requiresApproval) {
                                stringResource(R.string.enr_anyone_who_uses_the_code_stays_pending)
                            } else {
                                stringResource(R.string.enr_anyone_who_uses_the_code_gets_in_right)
                            },
                            color = CodiceColors.TextLow,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.requiresApproval,
                        onCheckedChange = onRequiresApprovalChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CodiceColors.Canvas,
                            checkedTrackColor = LocalAccent.current.primary,
                        ),
                    )
                }
            }

            Text(
                text = stringResource(R.string.enr_existing_code_hint),
                color = CodiceColors.TextLow,
                fontSize = 12.sp,
            )

            Text(
                text = stringResource(R.string.enr_creating_it_generates_the_group_s_invi),
                color = CodiceColors.TextLow,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CreateCourseTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .clip(CircleShape)
                .background(CodiceColors.GlassHigh),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.enr_back), tint = CodiceColors.TextHigh)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            GradientText(text = stringResource(R.string.enr_create_course), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.enr_course_first_group_and_invitation_code),
                style = MaterialTheme.typography.bodySmall,
                color = CodiceColors.TextLow,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = CodiceColors.TextLow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun CourseTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String?,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val accent = LocalAccent.current
    Column {
        Text(label, color = CodiceColors.TextMid, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = CodiceColors.TextLow) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CodiceColors.TextHigh,
                unfocusedTextColor = CodiceColors.TextHigh,
                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                focusedBorderColor = accent.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                errorBorderColor = CodiceColors.Orange,
                cursorColor = accent.primary,
            ),
        )
        error?.let {
            Text(it, color = CodiceColors.Orange, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun TeacherCreateCourseScreenPreview() {
    TeacherCreateCourseScreen(
        state = TeacherCreateCourseUiState(code = "EIF411", fieldErrors = mapOf(CourseField.PERIOD to "El periodo es obligatorio")),
        onBack = {}, onCodeChange = {}, onNameChange = {}, onGroupNumberChange = {}, onPeriodChange = {},
        onRequiresApprovalChange = {}, onSubmit = {},
    )
}
