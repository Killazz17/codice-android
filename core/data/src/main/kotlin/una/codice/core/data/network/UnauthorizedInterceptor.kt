package una.codice.core.data.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import una.codice.core.data.local.SessionPreferences

/**
 * Si el servidor responde 401 a una request que llevaba token, el JWT venció o ya no vale: se olvida la sesión, y
 * `MainViewModel` (que observa la sesión) lleva a la pantalla de acceso. Sin esto la persona veía
 * errores genéricos para siempre con un token muerto guardado en el teléfono.
 *
 * Un 401 del login no cuenta (es "correo o contraseña incorrectos"), ni una request sin token.
 * Va después de [AuthInterceptor] para ver el header ya puesto. `runBlocking` es seguro aquí: corre en un hilo de red de
 * OkHttp, nunca en el principal.
 */
@Singleton
class UnauthorizedInterceptor @Inject constructor(
    private val sessionPreferences: SessionPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val carriedToken = request.header(AuthInterceptor.AUTHORIZATION) != null
        if (response.code == HTTP_UNAUTHORIZED && carriedToken && !request.url.encodedPath.contains(LOGIN_PATH)) {
            runBlocking { sessionPreferences.clear() }
        }
        return response
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val LOGIN_PATH = "/login"
    }
}
