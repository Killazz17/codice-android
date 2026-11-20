package una.codice.core.data.network

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import una.codice.core.data.local.SessionPreferences

/**
 * Un 401 con token significa "tu sesión ya no vale": se olvida la sesión y los datos locales. Un 401 sin token o del
 * login no, porque ahí el error es de las credenciales, no de una sesión vencida.
 */
class UnauthorizedInterceptorTest {

    private val sessionPreferences = mockk<SessionPreferences>(relaxed = true)
    private val interceptor = UnauthorizedInterceptor(sessionPreferences)

    private fun respond(url: String, code: Int, withToken: Boolean): Response {
        val request = Request.Builder().url(url).apply { if (withToken) header("Authorization", "Bearer abc") }.build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response
        return interceptor.intercept(chain)
    }

    @Test
    fun `un 401 con token cierra la sesion`() {
        val response = respond("https://api.test/api/v1/enrollments/my", 401, withToken = true)

        assertEquals(401, response.code)
        coVerify(exactly = 1) { sessionPreferences.clear() }
    }

    @Test
    fun `un 401 sin token no toca la sesion`() {
        respond("https://api.test/api/v1/courses", 401, withToken = false)

        coVerify(exactly = 0) { sessionPreferences.clear() }
    }

    @Test
    fun `un 401 del login es una credencial incorrecta y no cierra la sesion`() {
        respond("https://api.test/v1/users/login", 401, withToken = true)

        coVerify(exactly = 0) { sessionPreferences.clear() }
    }

    @Test
    fun `un 403 o un 200 no cierran la sesion`() {
        respond("https://api.test/api/v1/courses", 403, withToken = true)
        respond("https://api.test/api/v1/courses", 200, withToken = true)

        coVerify(exactly = 0) { sessionPreferences.clear() }
    }
}
