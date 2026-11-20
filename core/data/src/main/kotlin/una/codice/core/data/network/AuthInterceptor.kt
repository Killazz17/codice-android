package una.codice.core.data.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import una.codice.core.data.local.SessionPreferences

/**
 * Agrega `Authorization: Bearer <token>` a cada request con la sesión guardada.
 *
 * Así ningún `ApiService` tiene que recibir el token como parámetro: los repositorios piden datos
 * y este interceptor se encarga de probar quién es el usuario. Si la request ya trae su propio
 * header (como `/auth/me` justo después del login) o no hay sesión, la deja pasar tal cual.
 *
 * `runBlocking` es seguro aquí: OkHttp ejecuta los interceptores en sus hilos de red, nunca en el
 * hilo principal, y leer la sesión de DataStore es una lectura local muy corta.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionPreferences: SessionPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)

        val token = runBlocking { sessionPreferences.session.first()?.token }
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder().header(AUTHORIZATION, BEARER + token).build(),
        )
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
    }
}
