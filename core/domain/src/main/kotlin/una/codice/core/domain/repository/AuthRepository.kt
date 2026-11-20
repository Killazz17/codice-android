package una.codice.core.domain.repository

import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.result.DomainResult
import kotlinx.coroutines.flow.Flow

/**
 * El contrato de autenticación: **qué** se puede hacer, sin decir **cómo**.
 *
 * ### La inversión de dependencias, explicada
 *
 * Esta interfaz vive en `:core:domain`, pero quien la implementa
 * (`AuthRepositoryImpl`, issue #4) vive en `:core:data`. Es decir: la capa de
 * abajo (datos) depende de la de arriba (dominio), y no al revés. Suena
 * invertido, y por eso se llama así.
 *
 * ¿Para qué tanto rodeo? Porque los casos de uso solo conocen esta interfaz.
 * Eso permite dos cosas muy concretas:
 *
 * - **Probar sin Android.** En los tests le pasamos un `FakeAuthRepository`
 *   escrito a mano, y las reglas de negocio se verifican con JUnit puro: sin
 *   emulador, sin Retrofit, sin DataStore, en milisegundos.
 * - **Cambiar el origen de los datos sin tocar la lógica.** Hoy los datos salen
 *   de un data source falso en memoria; mañana saldrán de la API real. Los
 *   casos de uso y las pantallas no se enteran.
 *
 * ### Por qué las funciones son `suspend`
 *
 * `suspend` marca una función que puede pausarse y reanudarse sin bloquear el
 * hilo. Como estas operaciones van a red o a disco, deben poder tardar sin
 * congelar la interfaz. Kotlin obliga a llamarlas desde una corrutina, así que
 * es imposible ejecutarlas por accidente en el hilo principal.
 */
interface AuthRepository {

    /**
     * Autentica con credenciales cuyo **formato ya fue validado** por
     * [una.codice.core.domain.usecase.auth.LoginUseCase].
     *
     * @return la [Session] recién creada, o un
     *   [una.codice.core.domain.error.DomainError.InvalidCredentials] si no
     *   coinciden con ninguna cuenta.
     */
    suspend fun login(email: String, password: String): DomainResult<Session>

    /**
     * Da de alta al usuario y **deja la sesión iniciada automáticamente**, para
     * que no tenga que escribir sus datos otra vez justo después de crearlos.
     *
     * Si el correo ya existe devuelve un `Validation(EMAIL, ...)`, no una
     * excepción: que alguien intente registrarse dos veces es un caso normal
     * del flujo, no un error de programación.
     */
    suspend fun register(newUser: NewUser): DomainResult<Session>

    /**
     * La sesión persistida, expuesta como un **stream** ([Flow]).
     *
     * Emite `null` cuando no hay nadie autenticado.
     *
     * ### Por qué un `Flow` y no un `suspend fun getSession(): Session?`
     *
     * Con una función normal habría que *preguntar* "¿hay sesión?" en cada
     * momento en que pudiera haber cambiado, y acordarse de volver a preguntar
     * tras cerrar sesión. Con un `Flow`, la pantalla se **suscribe una vez** y
     * reacciona sola: en el instante en que se cierra sesión o expira el token,
     * el flujo emite `null` y la app vuelve al login sin que nadie lo ordene
     * explícitamente.
     */
    fun currentSession(): Flow<Session?>

    /** Borra por completo la sesión persistida en el dispositivo. */
    suspend fun logout(): DomainResult<Unit>

    /**
     * Cambia la contraseña de la sesión iniciada. La sesión actual sigue valiendo.
     * Falla con [una.codice.core.domain.error.DomainError.Validation] si la actual no es correcta.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit>
}
