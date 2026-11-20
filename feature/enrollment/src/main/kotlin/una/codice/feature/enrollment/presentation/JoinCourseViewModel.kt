package una.codice.feature.enrollment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.usecase.enrollment.ObserveMyEnrollmentsUseCase
import una.codice.core.domain.usecase.enrollment.RequestJoinUseCase
import una.codice.core.domain.usecase.enrollment.ResolveInviteCodeUseCase
import javax.inject.Inject
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.repository.EnrollmentRepository

/**
 * Gestiona la lógica de la pantalla "Unirte a un curso".
 */
@HiltViewModel
class JoinCourseViewModel @Inject constructor(
    private val resolveInviteCodeUseCase: ResolveInviteCodeUseCase,
    private val requestJoinUseCase: RequestJoinUseCase,
    private val authRepository: AuthRepository,
    private val courseSelection: CourseSelectionRepository,
    private val enrollmentRepository: EnrollmentRepository,
    observeMyEnrollmentsUseCase: ObserveMyEnrollmentsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    private val _events = Channel<JoinEvent>(capacity = Channel.CONFLATED)
    val events = _events.receiveAsFlow()

    // Observa en tiempo real los cursos del alumno. Se actualiza automáticamente 
    // cuando se solicita unirse a uno nuevo.
    val myEnrollments: StateFlow<UiState<List<una.codice.core.domain.model.Enrollment>>> =
        observeMyEnrollmentsUseCase()
            .map { list -> UiState.Success(list) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UiState.Loading
            )

    /**
     * Se llama cada vez que el usuario escribe en el campo del código.
     * Aplica el formato automático (Mayúsculas y guiones).
     */
    fun onCodeChange(newCode: String) {
        // Solo mayúsculas, letras, números y guiones. Los guiones no se acomodan mientras se escribe (el curso tiene un
        // largo variable y no se sabe dónde termina); se acomodan al buscar, en normalizeCode.
        val cleaned = newCode.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(MAX_CODE_INPUT)

        _state.value = _state.value.copy(
            code = cleaned,
            errorMessage = null,
            groupFound = null
        )
    }

    /**
     * Valida si el código existe en el sistema.
     * Si el código es válido, dispara la aparición del diálogo de confirmación.
     */
    fun onResolve() {
        // Se acomodan los guiones aunque la persona los haya escrito sin ellos (o pegado el código tal cual).
        val normalized = normalizeCode(_state.value.code)
        if (normalized.replace("-", "").length !in MIN_CODE_CHARS..MAX_CODE_CHARS) return

        _state.value = _state.value.copy(code = normalized, isLoading = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = resolveInviteCodeUseCase(normalized)) {
                is DomainResult.Success -> {
                    // Grupo encontrado, el UI reaccionará mostrando el diálogo
                    _state.value = _state.value.copy(
                        isLoading = false,
                        groupFound = result.data
                    )
                }
                is DomainResult.Failure -> {
                    // Error (ej: código no existe o formato incorrecto)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    /**
     * Envía la solicitud de unión definitiva tras la confirmación del usuario.
     */
    fun onConfirmJoin() {
        val code = _state.value.code
        
        _state.value = _state.value.copy(isLoading = true)

        viewModelScope.launch {
            // Obtenemos el ID del usuario actual de la sesión
            val studentId = authRepository.currentSession().firstOrNull()?.userId ?: "user-1"
            
            when (val result = requestJoinUseCase(code, studentId)) {
                is DomainResult.Success -> {
                    // Solicitud enviada con éxito
                    val professor = _state.value.groupFound?.professorName.orEmpty()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        code = "",
                        groupFound = null
                    )
                    _events.trySend(JoinEvent.Success(professor))
                }
                is DomainResult.Failure -> {
                    // Error al procesar la solicitud
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    /** El estudiante sale del curso (o cancela su solicitud). La lista de cursos se vuelve a pedir sola al terminar. */
    fun onLeave(enrollmentId: String) {
        viewModelScope.launch {
            when (val result = enrollmentRepository.leaveEnrollment(enrollmentId)) {
                is DomainResult.Success -> _events.trySend(JoinEvent.Left)
                is DomainResult.Failure -> _events.trySend(JoinEvent.ShowMessage(result.error.message))
            }
        }
    }

    /** Deja elegido el curso que se tocó, para que su mapa de cobertura se abra con ese curso. */
    fun onOpenCourse(groupId: String) {
        courseSelection.select(groupId)
    }

    /**
     * Abre el escáner de QR.
     */
    fun onQrClick() {
        _state.value = _state.value.copy(isScannerOpen = true)
    }

    /**
     * Cierra el escáner de QR.
     */
    fun onDismissScanner() {
        _state.value = _state.value.copy(isScannerOpen = false)
    }

    /**
     * Se llama cuando el escáner detecta un código.
     */
    fun onQrDetected(scannedCode: String) {
        // El código del profesor suele ser el PIN formateado o crudo.
        // Si detectamos algo, cerramos el scanner y lo procesamos.
        _state.value = _state.value.copy(
            isScannerOpen = false,
            code = scannedCode
        )
        // Intentamos validar de una vez
        onResolve()
    }

    /**
     * Cierra el diálogo de confirmación sin realizar ninguna acción.
     */
    fun onDismissDialog() {
        _state.value = _state.value.copy(groupFound = null)
    }
}

/**
 * Eventos que ocurren una sola vez en la pantalla.
 */
sealed interface JoinEvent {
    /** La solicitud salió; el texto del aviso lo arma la pantalla para que salga en el idioma elegido. */
    data class Success(val professorName: String) : JoinEvent
    data class ShowMessage(val message: String) : JoinEvent

    /** El estudiante salió de un curso; el texto del aviso lo arma la pantalla. */
    data object Left : JoinEvent
}

/** Un código es CURSO-GRUPO-CLAVE: el curso de 5 a 10 caracteres (lo que acepta el servidor), el grupo 2 y la clave 4. */
internal const val MIN_CODE_CHARS = 11
internal const val MAX_CODE_CHARS = 16
private const val MAX_CODE_INPUT = 18

/** Acomoda los guiones aunque se hayan escrito sin ellos: la clave son los últimos 4 caracteres y el grupo los 2 anteriores. */
internal fun normalizeCode(raw: String): String {
    val clean = raw.uppercase().filter { it.isLetterOrDigit() }
    if (clean.length !in MIN_CODE_CHARS..MAX_CODE_CHARS) return raw.uppercase().trim()
    val cut = clean.length - 6
    return "${clean.substring(0, cut)}-${clean.substring(cut, cut + 2)}-${clean.substring(cut + 2)}"
}
