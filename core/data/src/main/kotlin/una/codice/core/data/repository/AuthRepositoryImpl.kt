package una.codice.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import una.codice.core.data.local.SessionPreferences
import una.codice.core.data.network.AuthInterceptor
import una.codice.core.data.network.apiCallDetailed
import una.codice.core.data.network.auth.AuthApiService
import una.codice.core.data.network.auth.LoginRequestDto
import una.codice.core.data.network.auth.LoginResponseDto
import una.codice.core.data.network.auth.toSession
import una.codice.core.data.network.auth.toSignUpDto
import retrofit2.HttpException
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.data.network.auth.ChangePasswordRequestDto

/**
 * Implementación de [AuthRepository] contra el backend real de Códice (Spring Boot).
 *
 * Login en dos pasos: `POST /api/v1/auth/login` devuelve el token en el header `Authorization`
 * y/o en el cuerpo del JSON, y con ese token `GET /api/v1/auth/me` devuelve el id y los roles del usuario para armar la [Session].
 * El registro crea la cuenta (`POST /api/v1/auth/signup`) y luego inicia sesión con las mismas
 * credenciales, porque el backend no devuelve token al registrar.
 *
 * [SessionPreferences] sigue siendo la única pieza que persiste en disco cuál es la sesión activa;
 * `AuthInterceptor` la lee para mandar el token en cada request.
 *
 * ### Por qué `currentSession()` y `logout()` siguen siendo 100% locales
 *
 * La sesión activa es un dato que ya vive en el dispositivo, y "cerrar sesión" en Códice significa
 * "olvidar el token guardado aquí": el JWT del backend no se revoca del lado del servidor.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService,
    private val sessionPreferences: SessionPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true },
) : AuthRepository {

    override suspend fun login(email: String, password: String): DomainResult<Session> {
        val resultado = apiCallDetailed(
            onHttpError = { code, body ->
                val exactMessage = parseErrorMessageFromBody(body)
                if (code == 401) {
                    DomainError.InvalidCredentials(exactMessage ?: "Correo o contraseña incorrectos")
                } else if (code in listOf(400, 422)) {
                    DomainError.Validation(DomainError.Field.PASSWORD, exactMessage ?: "Datos de acceso inválidos")
                } else {
                    exactMessage?.let { DomainError.Unknown(it) }
                }
            },
        ) {
            val response = authApiService.login(LoginRequestDto(username = email, password = password))
            if (!response.isSuccessful) throw HttpException(response)

            val headerToken = response.headers()[AuthInterceptor.AUTHORIZATION]
                ?.removePrefix(AuthInterceptor.BEARER)
                ?.takeIf { it.isNotBlank() }

            val bodyString = response.body()?.string()
            val bodyToken = bodyString?.let { parseTokenFromBody(it) }

            val token = headerToken ?: bodyToken
                ?: throw IllegalStateException("El login no devolvió el token en el header Authorization ni en el cuerpo")

            authApiService.me(AuthInterceptor.BEARER + token).toSession(token)
        }

        // Se guarda la sesión ANTES de devolver el resultado: si la pantalla de arranque observa
        // currentSession() justo después de que login() termine, ya debe encontrarla persistida.
        if (resultado is DomainResult.Success) {
            sessionPreferences.saveSession(resultado.data)
        }
        return resultado
    }

    private fun parseErrorMessageFromBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                val jsonMap = json.decodeFromString<Map<String, String>>(trimmed)
                jsonMap["message"] ?: jsonMap["error"] ?: jsonMap["detail"] ?: trimmed
            } else {
                trimmed.takeIf { it.length < 150 }
            }
        }.getOrNull() ?: body.takeIf { it.length < 150 }
    }

    private fun parseTokenFromBody(bodyString: String): String? {
        return runCatching {
            val dto = json.decodeFromString<LoginResponseDto>(bodyString)
            dto.token ?: dto.accessToken ?: dto.jwt
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override suspend fun register(newUser: NewUser): DomainResult<Session> {
        val registro = apiCallDetailed(
            // El backend responde 409 (Conflict), 400 o 422 cuando el correo ya está registrado.
            onHttpError = { code, body ->
                val bodyLower = body?.lowercase().orEmpty()
                val isDuplicateEmail = code in listOf(400, 409, 422) ||
                    "email" in bodyLower || "correo" in bodyLower ||
                    "exist" in bodyLower || "registrad" in bodyLower || "already" in bodyLower

                if (isDuplicateEmail) {
                    DomainError.Validation(DomainError.Field.EMAIL, "Ese correo ya está registrado")
                } else null
            },
        ) {
            authApiService.signUp(newUser.toSignUpDto())
        }

        if (registro is DomainResult.Failure) return registro

        // Registrarse deja la sesión iniciada automáticamente.
        val loginResult = login(newUser.email, newUser.password)
        if (loginResult is DomainResult.Success) return loginResult

        // Si el login automático de inmediato falló (por ejemplo por breve latencia de la BD),
        // reintentamos una vez tras 500ms antes de devolver error.
        delay(500.milliseconds)
        return login(newUser.email, newUser.password)
    }

    override fun currentSession(): Flow<Session?> = sessionPreferences.session

    override suspend fun logout(): DomainResult<Unit> {
        sessionPreferences.clear()
        return DomainResult.Success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> =
        apiCallDetailed(
            onHttpError = { code, body ->
                val exactMessage = parseErrorMessageFromBody(body)
                when (code) {
                    // 422: la contraseña actual no es correcta (o la nueva es igual). No es 401: eso cerraría la sesión.
                    422 -> DomainError.Validation(DomainError.Field.PASSWORD, exactMessage ?: "La contraseña actual no es correcta")
                    400 -> DomainError.Validation(DomainError.Field.PASSWORD, exactMessage ?: "La contraseña nueva no cumple las reglas")
                    else -> null
                }
            },
        ) {
            authApiService.changePassword(ChangePasswordRequestDto(currentPassword = currentPassword, newPassword = newPassword))
        }
}
