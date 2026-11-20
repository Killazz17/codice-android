package una.codice.core.data.network.auth

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Los endpoints de autenticación del backend de Códice (Spring Boot).
 *
 * El login lo atiende el backend en `/api/v1/auth/login` y devuelve el token en el
 * header `Authorization` y/o en el cuerpo del JSON; por eso [login] devuelve el [Response]
 * completo en vez de un DTO: `AuthRepositoryImpl` necesita leer ese header o el cuerpo. Con
 * el token se pide [me] para saber el id y el rol del usuario.
 *
 * [signUp] y [me] devuelven el DTO directamente: si el servidor responde con un código de error,
 * Retrofit lanza [retrofit2.HttpException] y `AuthRepositoryImpl` lo traduce con
 * [una.codice.core.data.network.apiCall].
 */
interface AuthApiService {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<ResponseBody>

    @POST("api/v1/auth/signup")
    suspend fun signUp(@Body request: SignUpRequestDto): UserDto

    /** El token se pasa explícito: justo después del login todavía no está guardado en la sesión. */
    @GET("api/v1/auth/me")
    suspend fun me(@Header("Authorization") authorization: String): UserDto

    /** 204 sin cuerpo. Con la contraseña actual mal el backend responde 422 (no 401, que cerraría la sesión). */
    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto)
}
