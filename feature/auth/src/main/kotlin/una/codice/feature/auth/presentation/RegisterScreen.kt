package una.codice.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.glassPanel
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.UserRole
import una.codice.feature.auth.R
import androidx.compose.ui.res.stringResource
import una.codice.core.domain.usecase.auth.ValidatePasswordUseCase

/**
 * Punto de entrada de la pantalla de registro: conecta el [RegisterViewModel]
 * real (inyectado por Hilt) con el composable sin estado [RegisterScreen].
 * Mismo criterio que [AuthRoute] para el login.
 *
 * @param onAuthenticated se llama una sola vez, cuando la cuenta se crea con
 *   éxito, con el rol elegido.
 * @param onNavigateToLogin se llama al tocar "¿Ya tenés cuenta? Iniciar
 *   sesión" — quien monta esta pantalla decide a qué ruta corresponde (en la
 *   práctica, volver atrás al login).
 */
@Composable
fun RegisterRoute(
    onAuthenticated: (UserRole) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateAsRole.collect(onAuthenticated)
    }

    RegisterScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onFullNameChanged = viewModel::onFullNameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onPasswordConfirmationChanged = viewModel::onPasswordConfirmationChanged,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onRoleSelected = viewModel::onRoleSelected,
        onSubmit = viewModel::onSubmit,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
    )
}

/**
 * La pantalla de registro, sin estado propio — mismo criterio que
 * [AuthScreen]: todo sale de [state], cada interacción se delega a un
 * callback, y así es trivial de probar con `@Preview` sin depender de Hilt.
 *
 * Reutiliza [LogoBadge], [AuthField] y
 * [RoleSelector] de `AuthComponents.kt` para mantener el mismo lenguaje
 * visual que el login sin duplicar layout.
 */
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onEmailChanged: (String) -> Unit,
    onFullNameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onRoleSelected: (UserRole) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        LogoBadge()

        Spacer(Modifier.height(2.dp))
        GradientText(text = stringResource(R.string.auth_create_your_account), fontSize = 34.sp, fontWeight = FontWeight.Black)

        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.auth_a_few_details_and_you_re_ready_to_phot),
            color = AuthColors.TextMid,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(20.dp))

        // El rol va PRIMERO: es la decisión que cambia lo demás (qué verás al entrar) y, al final del formulario,
        // casi nadie llegaba a verla.
        Text(
            text = stringResource(R.string.auth_how_will_you_use_codice),
            color = AuthColors.TextHigh,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        RoleSelector(selectedRole = state.role, onRoleSelected = onRoleSelected)
        // El error de "elige un rol" se muestra en coral debajo de las tarjetas.
        state.fieldErrors[DomainError.Field.ROLE]?.let { mensaje ->
            Spacer(Modifier.height(6.dp))
            Text(text = mensaje, color = AuthColors.Coral, fontSize = 12.sp)
        }

        Spacer(Modifier.height(18.dp))

        RegisterFormCard(
            state = state,
            focusManager = focusManager,
            keyboardController = keyboardController,
            onEmailChanged = onEmailChanged,
            onFullNameChanged = onFullNameChanged,
            onPasswordChanged = onPasswordChanged,
            onPasswordConfirmationChanged = onPasswordConfirmationChanged,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
            onSubmit = onSubmit,
        )

        Spacer(Modifier.height(24.dp))
        LoginLink(onClick = onNavigateToLogin)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RegisterFormCard(
    state: RegisterUiState,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onEmailChanged: (String) -> Unit,
    onFullNameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    val passwordVisualTransformation = if (state.isPasswordVisible) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }
    val visibilityIcon: @Composable () -> Unit = {
        IconButton(onClick = onTogglePasswordVisibility) {
            Icon(
                imageVector = if (state.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (state.isPasswordVisible) stringResource(R.string.auth_hide_password) else stringResource(R.string.auth_show_password),
                tint = AuthColors.TextLow,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(22.dp)
            .padding(18.dp),
    ) {
        AuthField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = stringResource(R.string.auth_institutional_email),
            placeholder = stringResource(R.string.auth_email_example),
            errorMessage = state.fieldErrors[DomainError.Field.EMAIL],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        )

        Spacer(Modifier.height(12.dp))

        AuthField(
            value = state.fullName,
            onValueChange = onFullNameChanged,
            label = stringResource(R.string.auth_full_name),
            placeholder = stringResource(R.string.auth_full_name_example),
            // FIRST_NAME es la llave que RegisterViewModel usa para el campo
            // de nombre completo (ver el KDoc de RegisterUiState.fieldErrors).
            errorMessage = state.fieldErrors[DomainError.Field.FIRST_NAME],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        )

        Spacer(Modifier.height(12.dp))

        AuthField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = stringResource(R.string.auth_password),
            helperText = stringResource(R.string.auth_password_hint, ValidatePasswordUseCase.MIN_LENGTH),
            errorMessage = state.fieldErrors[DomainError.Field.PASSWORD],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            visualTransformation = passwordVisualTransformation,
            trailingIcon = visibilityIcon,
        )

        Spacer(Modifier.height(12.dp))

        AuthField(
            value = state.passwordConfirmation,
            onValueChange = onPasswordConfirmationChanged,
            label = stringResource(R.string.auth_confirm_password),
            errorMessage = state.fieldErrors[DomainError.Field.PASSWORD_CONFIRMATION],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    onSubmit()
                },
            ),
            visualTransformation = passwordVisualTransformation,
            trailingIcon = visibilityIcon,
        )

        Spacer(Modifier.height(12.dp))

        CtaButton(text = stringResource(R.string.auth_create_account), onClick = onSubmit, isLoading = state.isLoading)
    }
}

/** "¿Ya tenés cuenta? Iniciar sesión": el camino de vuelta al login. */
@Composable
private fun LoginLink(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val accent = LocalAccent.current
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.auth_already_have_an_account))
            withStyle(SpanStyle(color = accent.primary, fontWeight = FontWeight.Bold)) { append(stringResource(R.string.auth_sign_in_2)) }
        },
        color = AuthColors.TextMid,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    )
}

@Preview(name = "Modo oscuro", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    // MaterialTheme y no CodiceTheme: ver el mismo comentario en AuthScreen.kt.
    MaterialTheme {
        RegisterScreen(
            state = RegisterUiState(
                email = "ana@una.ac.cr",
                fullName = "Ana Rojas",
                role = UserRole.STUDENT,
                fieldErrors = mapOf(
                    DomainError.Field.PASSWORD_CONFIRMATION to "Las contraseñas no coinciden",
                ),
            ),
            onEmailChanged = {},
            onFullNameChanged = {},
            onPasswordChanged = {},
            onPasswordConfirmationChanged = {},
            onTogglePasswordVisibility = {},
            onRoleSelected = {},
            onSubmit = {},
            onNavigateToLogin = {},
        )
    }
}
