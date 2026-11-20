package una.codice.feature.auth.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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

/**
 * Punto de entrada de la pantalla de login: conecta el [AuthViewModel] real
 * (inyectado por Hilt) con el composable sin estado [AuthScreen].
 *
 * Es la única función de este archivo que sabe que existe un ViewModel — el
 * criterio de aceptación del issue #6 dice explícitamente que el composable
 * de la pantalla no debe llamar a ningún repositorio, y aquí es donde se
 * traza esa línea.
 *
 * @param onAuthenticated se llama una sola vez, cuando el login tiene éxito,
 *   con el rol de la sesión.
 * @param onNavigateToRegister se llama al tocar "¿No tenés cuenta?
 *   Registrate" — quien monta esta pantalla decide a qué ruta corresponde.
 */
@Composable
fun AuthRoute(
    onAuthenticated: (UserRole) -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        // collect y no collectAsState: navegar es un evento de una sola vez,
        // no algo que deba convertirse en un valor de estado recordado.
        viewModel.navigateAsRole.collect(onAuthenticated)
    }

    AuthScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::onSubmit,
        onNavigateToRegister = onNavigateToRegister,
        modifier = modifier,
    )
}

/**
 * La pantalla de login, sin estado propio: todo lo que dibuja sale de
 * [state], y cada interacción del usuario se delega a los callbacks
 * recibidos. Así es trivial de probar con `@Preview` y no depende de Hilt.
 *
 * Sigue el layout de la pantalla "Onboarding y acceso" de Figma (nodo
 * `24:4`): logo, título, una frase, la tarjeta con los campos, el botón
 * "Entrar", el atajo de Google (deshabilitado, ver [AuthViewModel] o el plan
 * del issue #6 para el porqué) y el enlace a la pantalla de registro.
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            // Sin fondo propio: el fondo aurora de CodiceScaffold (en ámbar, la identidad del acceso) se extiende por
            // debajo de la barra de estado, y solo el CONTENIDO se corre para no quedar tapado por sus íconos.
            .windowInsetsPadding(WindowInsets.systemBars)
            // verticalScroll es lo que cumple el criterio de aceptación "con
            // el teclado abierto, el botón sigue alcanzable": en una pantalla
            // chica, el teclado deja de tapar el botón porque todo el
            // contenido puede desplazarse hacia arriba.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        LogoBadge()

        Spacer(Modifier.height(2.dp))
        GradientText(text = "Códice", fontSize = 44.sp, fontWeight = FontWeight.Black)

        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.auth_tagline),
            color = AuthColors.TextMid,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(20.dp))

        LoginFormCard(
            state = state,
            focusManager = focusManager,
            keyboardController = keyboardController,
            onEmailChanged = onEmailChanged,
            onPasswordChanged = onPasswordChanged,
            onTogglePasswordVisibility = onTogglePasswordVisibility,
            onSubmit = onSubmit,
        )

        Spacer(Modifier.height(24.dp))
        RegisterLink(onClick = onNavigateToRegister)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LoginFormCard(
    state: AuthUiState,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
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
            value = state.password,
            onValueChange = onPasswordChanged,
            label = stringResource(R.string.auth_password),
            errorMessage = state.fieldErrors[DomainError.Field.PASSWORD],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    onSubmit()
                },
            ),
            visualTransformation = if (state.isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (state.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (state.isPasswordVisible) stringResource(R.string.auth_hide_password) else stringResource(R.string.auth_show_password),
                        tint = AuthColors.TextLow,
                    )
                }
            },
        )

        if (state.generalError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.generalError,
                color = AuthColors.Coral,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(12.dp))

        // El botón toma el degradado de la zona (ámbar y naranja en el acceso), con brillo y destello.
        CtaButton(text = stringResource(R.string.auth_sign_in), onClick = onSubmit, isLoading = state.isLoading)
    }
}

/**
 * "¿No tenés cuenta? Registrate": el único camino a [RegisterScreen] desde el
 * login, ahora que crear una cuenta ya no ocurre solo por intentar entrar.
 */
@Composable
private fun RegisterLink(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val accent = LocalAccent.current
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.auth_don_t_have_an_account))
            withStyle(SpanStyle(color = accent.primary, fontWeight = FontWeight.Bold)) { append(stringResource(R.string.auth_sign_up)) }
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
private fun AuthScreenPreview() {
    // MaterialTheme y no CodiceTheme: CodiceTheme vive en :app, y :feature:auth
    // no puede depender de :app (seria una dependencia circular, ya que :app
    // es quien depende de :feature:auth). AuthScreen ya pinta su propio fondo
    // y sus propios colores (AuthColors), asi que el tema exterior apenas se
    // nota - solo aporta los valores por defecto de Material3 que Compose
    // exige tener disponibles en el arbol de composicion.
    MaterialTheme {
        AuthScreen(
            state = AuthUiState(
                email = "ana@una.ac.cr",
                fieldErrors = mapOf(DomainError.Field.PASSWORD to "Correo o contraseña incorrectos"),
            ),
            onEmailChanged = {},
            onPasswordChanged = {},
            onTogglePasswordVisibility = {},
            onSubmit = {},
            onNavigateToRegister = {},
        )
    }
}
