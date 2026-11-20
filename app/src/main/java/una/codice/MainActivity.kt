package una.codice

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import una.codice.core.designsystem.theme.CodiceAccents
import una.codice.core.designsystem.theme.ProvideAccent
import una.codice.core.domain.model.UserRole
import una.codice.presentation.lock.LockOverlay
import una.codice.presentation.navigation.CodiceNavHost
import una.codice.presentation.navigation.MainViewModel
import una.codice.presentation.navigation.SessionState
import una.codice.presentation.ui.theme.CodiceTheme
import una.codice.settings.AppSettings
import una.codice.settings.BiometricGate
import una.codice.settings.shouldRelock

/**
 * Unica Activity de la app.
 *
 * ### Por que una sola Activity
 *
 * Con Jetpack Compose y Navigation Compose, cada "pantalla" es una funcion
 * @Composable, no una Activity. Toda la navegacion ocurre dentro de esta
 * Activity intercambiando composables mediante [CodiceNavHost]. Eso hace las
 * transiciones mas rapidas y el estado mucho mas facil de compartir entre
 * pantallas.
 *
 * ### Por que FragmentActivity y no ComponentActivity
 *
 * `BiometricPrompt` (el aviso de huella) solo se puede mostrar sobre una
 * FragmentActivity. FragmentActivity extiende ComponentActivity, asi que nada
 * de lo de Compose cambia.
 *
 * ### @AndroidEntryPoint
 *
 * Marca este punto de entrada de Android para que Hilt pueda inyectar
 * dependencias aqui y en los ViewModels que se pidan desde sus composables.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    /** Mientras sea true, una pantalla opaca tapa la app y se pide la huella. */
    private var locked by mutableStateOf(false)

    /** Cuando la app salió de pantalla (reloj `elapsedRealtime`); nulo si está a la vista. */
    private var backgroundedAt: Long? = null

    private var promptShowing = false

    // Corre antes que onCreate, en el primer instante de la Activity: aquí se aplica el idioma elegido, para que
    // todos los stringResource de la app salgan en ese idioma.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // La huella se pide al abrir la app de cero. Girar la pantalla o cambiar de idioma (que recrean la Activity)
        // no la piden de nuevo si ya se había verificado. Pero si el sistema mató el proceso y lo restaura,
        // `verifiedThisProcess` ya no está y se pide otra vez: el estado guardado solo no basta para saltarla.
        val wasVerified = verifiedThisProcess && savedInstanceState?.getBoolean(KEY_LOCKED, true) == false
        locked = AppSettings.biometricLock(this) && !wasVerified

        // Permite que la app dibuje debajo de la barra de estado y la de
        // navegacion del sistema.
        //
        // SystemBarStyle.dark(): fuerza iconos claros en ambas barras, sin
        // importar si el sistema esta en modo claro u oscuro. Las 17
        // pantallas del documento de diseño (acceso, biblioteca, mapa de
        // cobertura, panel del profesor, etc.) son TODAS de fondo oscuro
        // (AuthColors.Canvas / NavColors.Canvas) - con el estilo "auto" por
        // defecto, un telefono en modo claro pintaria iconos oscuros sobre
        // ese fondo y quedarian practicamente invisibles, como paso en la
        // primera prueba en el emulador del issue #6.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )

        // setContent reemplaza al setContentView(R.layout.xxx) de las Views:
        // aqui empieza el arbol de composables.
        setContent {
            CodiceTheme {
                // MainViewModel decide, observando la sesion guardada, si el
                // NavHost arranca en el logotipo, el login, o directo en el
                // grafo del rol correspondiente. Ver el KDoc de
                // CodiceNavHost para el porque de que esta decision se tome
                // ANTES de montar el NavHost, y no despues.
                val mainViewModel: MainViewModel = hiltViewModel()
                val sessionState by mainViewModel.sessionState.collectAsStateWithLifecycle()

                // El bloqueo solo tiene sentido con una sesión abierta: en el login no hay nada que proteger.
                val showLock = locked && sessionState is SessionState.LoggedIn

                // Sin sesión no se arrastra un bloqueo pendiente: si no, al iniciar sesión pediría la huella de golpe.
                LaunchedEffect(sessionState) { if (sessionState is SessionState.LoggedOut) locked = false }
                LaunchedEffect(showLock) { if (showLock) promptUnlock() }

                Box(modifier = Modifier.fillMaxSize()) {
                    // La pantalla de bloqueo tapa la app a la vista, pero no para los lectores de pantalla (TalkBack):
                    // sin esto seguirían leyendo lo de abajo. Con `clearAndSetSemantics` desaparece de la accesibilidad
                    // mientras está bloqueado, sin quitarlo de la composición (así no se pierde la pila de navegación).
                    Box(modifier = Modifier.fillMaxSize().then(if (showLock) Modifier.clearAndSetSemantics { } else Modifier)) {
                        CodiceNavHost(sessionState = sessionState)
                    }

                    if (showLock) {
                        val role = (sessionState as SessionState.LoggedIn).role
                        ProvideAccent(if (role == UserRole.TEACHER) CodiceAccents.Teacher else CodiceAccents.Student) {
                            LockOverlay(onUnlock = ::promptUnlock)
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
    }

    override fun onStop() {
        super.onStop()
        // Una rotación también detiene la Activity un instante, pero eso no es "dejar la app".
        if (!isChangingConfigurations) backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val left = backgroundedAt
        backgroundedAt = null
        if (left != null && AppSettings.biometricLock(this) && shouldRelock(left, SystemClock.elapsedRealtime())) {
            locked = true
        }
    }

    private fun promptUnlock() {
        if (promptShowing) return
        // Si la persona quitó la huella y el bloqueo del teléfono, no hay con qué verificarla: abrir es mejor que
        // dejarla fuera para siempre de su propia app.
        if (!BiometricGate.isAvailable(this)) {
            locked = false
            return
        }
        promptShowing = true
        BiometricGate.authenticate(
            activity = this,
            onSuccess = {
                promptShowing = false
                verifiedThisProcess = true
                locked = false
            },
            // Cancelar deja la pantalla de bloqueo, con su botón "Desbloquear" para intentarlo otra vez.
            onFailure = { promptShowing = false },
        )
    }

    private companion object {
        const val KEY_LOCKED = "locked"

        /** Vive lo que vive el proceso: si Android lo mata y lo restaura, vuelve a false y se pide la huella. */
        var verifiedThisProcess = false
    }
}
