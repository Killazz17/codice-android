package una.codice.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import una.codice.R
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.auth.ChangePasswordUseCase
import una.codice.core.domain.usecase.auth.ValidatePasswordUseCase

data class ChangePasswordUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false,
)

/**
 * Cambiar la contraseña desde el Perfil. Las reglas (que la nueva sea válida, coincida con su confirmación y sea distinta)
 * viven en [ChangePasswordUseCase]; aquí solo se espera la respuesta y se muestra el error. Un error de contraseña actual
 * incorrecta NO cierra la sesión: el servidor responde 422, no 401.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changePassword: ChangePasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun submit(current: String, new: String, confirmation: String) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = changePassword(current, new, confirmation)) {
                is DomainResult.Success -> _state.update { it.copy(isSaving = false, done = true) }
                is DomainResult.Failure -> _state.update { it.copy(isSaving = false, errorMessage = result.error.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    /** Deja el estado limpio para la próxima vez que se abra el diálogo. */
    fun reset() {
        _state.value = ChangePasswordUiState()
    }
}

/** El diálogo con la contraseña actual, la nueva y su confirmación. Las contraseñas no se guardan al girar la pantalla. */
@Composable
internal fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val busy = state.isSaving

    // El ViewModel vive con la pantalla del Perfil: se limpia al abrir, para no ver el error de la vez anterior.
    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.reset()
            onChanged()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = CodiceColors.Glass,
        title = { Text(stringResource(R.string.app_change_password_title), color = CodiceColors.TextHigh) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.app_change_password_hint), color = CodiceColors.TextMid, fontSize = 13.sp)
                PasswordField(
                    value = current,
                    onValueChange = { current = it; viewModel.clearError() },
                    label = stringResource(R.string.app_pw_current),
                    visible = visible,
                    enabled = !busy,
                )
                PasswordField(
                    value = new,
                    onValueChange = { new = it; viewModel.clearError() },
                    label = stringResource(R.string.app_pw_new),
                    visible = visible,
                    enabled = !busy,
                    helper = stringResource(R.string.app_pw_rules, ValidatePasswordUseCase.MIN_LENGTH),
                )
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it; viewModel.clearError() },
                    label = stringResource(R.string.app_pw_confirm),
                    visible = visible,
                    enabled = !busy,
                )
                TextButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = CodiceColors.TextMid,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (visible) R.string.app_pw_hide else R.string.app_pw_show),
                        color = CodiceColors.TextMid,
                        fontSize = 13.sp,
                    )
                }
                state.errorMessage?.let { Text(it, color = CodiceColors.Coral, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.submit(current, new, confirmation) }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accent.primary)
                } else {
                    Text(stringResource(R.string.app_pw_save), color = accent.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.app_pw_cancel), color = CodiceColors.TextLow)
            }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    enabled: Boolean,
    helper: String? = null,
) {
    val accent = LocalAccent.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        supportingText = helper?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CodiceColors.TextHigh,
            unfocusedTextColor = CodiceColors.TextHigh,
            disabledTextColor = CodiceColors.TextMid,
            focusedBorderColor = accent.primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedLabelColor = accent.primary,
            unfocusedLabelColor = CodiceColors.TextMid,
            cursorColor = accent.primary,
            focusedSupportingTextColor = CodiceColors.TextLow,
            unfocusedSupportingTextColor = CodiceColors.TextLow,
        ),
    )
}
