package una.codice.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.local.FakePreferencesDataStore
import una.codice.core.data.local.SessionPreferences
import una.codice.core.data.network.auth.AuthApiService
import una.codice.core.data.network.auth.UserDto
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.result.DomainResult

/**
 * Tests de [AuthRepositoryImpl] contra un [AuthApiService] mockeado con
 * MockK (sin levantar el backend) y [SessionPreferences] real sobre un
 * archivo temporal. El login del backend devuelve el token en el header
 * `Authorization` y el usuario se pide a `/auth/me`; el registro crea la
 * cuenta y luego inicia sesión. Ver el KDoc de [una.codice.core.data.local.SessionPreferencesTest]
 * para el porqué de que esto no necesite Android, y para el porqué de que
 * cada "momento" tenga su propio `TestScope` cancelable.
 */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher todavía es experimental en esta versión
class AuthRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val estudianteDto = UserDto(id = 1, email = "ana@una.ac.cr", roles = listOf("ROLE_STUDENT"))
    private val profesorDto = estudianteDto.copy(roles = listOf("ROLE_TEACHER"))

    /** Respuesta exitosa del login: el backend manda el token en el header `Authorization`. */
    private fun loginOk(token: String = "token-1"): Response<okhttp3.ResponseBody> =
        Response.success("{}".toResponseBody("application/json".toMediaType()), headersOf("Authorization", "Bearer $token"))

    /** Un [AuthApiService] cuyo login funciona y cuyo `/auth/me` devuelve [usuario]. */
    private fun apiQueAutentica(usuario: UserDto = estudianteDto): AuthApiService = mockk<AuthApiService>().also { api ->
        coEvery { api.signUp(any()) } returns usuario
        coEvery { api.login(any()) } returns loginOk()
        coEvery { api.me(any()) } returns usuario
    }

    /** Construye la excepción que Retrofit lanza cuando el servidor responde un código de error. */
    private fun httpException(code: Int): HttpException {
        val body = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }

    /**
     * "Cierra la app" de verdad, esperando a que la cancelación termine antes
     * de continuar. `cancel()` a secas no espera, y DataStore puede seguir
     * viendo el archivo como "activo" un instante después de pedida la
     * cancelación.
     */
    private fun TestScope.cerrarDelTodo() {
        val job = coroutineContext.job
        runBlocking { job.cancelAndJoin() }
    }

    /** Crea un repositorio nuevo, con su propio scope, sobre [archivo] y el [authApiService] dado. */
    private fun crearRepositorio(
        archivo: File,
        authApiService: AuthApiService,
    ): Pair<TestScope, AuthRepositoryImpl> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { archivo },
        )
        val repository = AuthRepositoryImpl(
            authApiService = authApiService,
            sessionPreferences = SessionPreferences(dataStore),
        )
        return scope to repository
    }

    private fun nuevoUsuario(email: String = "ana@una.ac.cr", role: UserRole = UserRole.STUDENT) = NewUser(
        firstName = "Ana",
        lastName = "Rojas",
        email = email,
        password = "codice2026",
        role = role,
    )

    @Test
    fun `login con credenciales correctas guarda la sesion y la devuelve`() {
        val api = apiQueAutentica()
        val (scope, repository) = crearRepositorio(File(tempFolder.root, "login_ok.preferences_pb"), api)

        scope.runTest {
            val resultado = repository.login("ana@una.ac.cr", "codice2026")

            val session = (resultado as DomainResult.Success).data
            assertEquals(UserRole.STUDENT, session.role)
            assertEquals("token-1", session.token)
            assertEquals("1", session.userId)
            // Y quedó persistida, no solo devuelta en el resultado.
            assertEquals(session, repository.currentSession().first())
        }
    }

    @Test
    fun `login extrae el token del cuerpo del JSON si no viene en el header Authorization`() {
        val api = mockk<AuthApiService>()
        val jsonBody = """{"token": "body-token-99"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.login(any()) } returns Response.success(jsonBody)
        coEvery { api.me("Bearer body-token-99") } returns estudianteDto

        val (scope, repository) = crearRepositorio(File(tempFolder.root, "login_body.preferences_pb"), api)

        scope.runTest {
            val resultado = repository.login("ana@una.ac.cr", "codice2026")

            val session = (resultado as DomainResult.Success).data
            assertEquals("body-token-99", session.token)
            assertEquals(session, repository.currentSession().first())
        }
    }

    /**
     * Un login fallido no debe alterar ninguna sesión que ya existiera. Se
     * registra primero (lo que sí deja una sesión guardada) para comprobar
     * que el intento de login con la contraseña equivocada la deja intacta,
     * en vez de borrarla o corromperla.
     */
    @Test
    fun `login con credenciales incorrectas no toca la sesion existente`() {
        val api = apiQueAutentica()
        // El primer login (el automático del registro) funciona; el segundo, con otra clave, no.
        coEvery { api.login(any()) } returns loginOk() andThenThrows httpException(401)
        val (scope, repository) = crearRepositorio(File(tempFolder.root, "login_mal.preferences_pb"), api)

        scope.runTest {
            val sesionOriginal = (repository.register(nuevoUsuario()) as DomainResult.Success).data

            val resultado = repository.login("ana@una.ac.cr", "otraCosa9")

            assertTrue((resultado as DomainResult.Failure).error is DomainError.InvalidCredentials)
            assertEquals(sesionOriginal, repository.currentSession().first())
        }
    }

    @Test
    fun `registrarse inicia sesion automaticamente con el rol elegido`() {
        val api = apiQueAutentica(profesorDto)
        val (scope, repository) = crearRepositorio(File(tempFolder.root, "registro.preferences_pb"), api)

        scope.runTest {
            val resultado = repository.register(nuevoUsuario(role = UserRole.TEACHER))

            val session = (resultado as DomainResult.Success).data
            assertEquals(UserRole.TEACHER, session.role)
            assertEquals(session, repository.currentSession().first())
        }
    }

    /** Criterio de aceptación literal: correo repetido -> validación, no excepción. */
    @Test
    fun `un correo repetido en el registro devuelve error de validacion`() {
        val api = apiQueAutentica()
        coEvery { api.signUp(any()) } throws httpException(409)
        val (scope, repository) = crearRepositorio(File(tempFolder.root, "correo_repetido.preferences_pb"), api)

        scope.runTest {
            val resultado = repository.register(nuevoUsuario())

            val validacion = (resultado as DomainResult.Failure).error as DomainError.Validation
            assertEquals(DomainError.Field.EMAIL, validacion.field)
        }
    }

    /**
     * Criterio de aceptación literal: cerrar sesión borra el token y vuelve al
     * login. Usa [una.codice.core.data.local.FakePreferencesDataStore] en vez
     * de un archivo real — ver su KDoc para el porqué: escribir un resultado
     * vacío sobre un DataStore de archivo con contenido previo falla de forma
     * reproducible en este entorno de Windows, ajeno al código bajo prueba.
     */
    @Test
    fun `cerrar sesion borra el token guardado`() = runTest {
        val api = apiQueAutentica()
        val repository = AuthRepositoryImpl(
            authApiService = api,
            sessionPreferences = SessionPreferences(FakePreferencesDataStore()),
        )
        repository.register(nuevoUsuario())
        assertTrue(repository.currentSession().first() != null)

        repository.logout()

        assertNull(repository.currentSession().first())
    }

    /**
     * Criterio de aceptación literal: "la contraseña nunca se guarda en
     * DataStore". A diferencia del test equivalente en SessionPreferencesTest
     * (que solo confirma que Session no tiene ese campo), aquí se pasa una
     * contraseña real de punta a punta —a través de register()— y se
     * inspecciona el DataStore subyacente para confirmar que ese valor exacto
     * nunca quedó escrito en disco. Ni [UserDto] ni la respuesta del login
     * llevan la contraseña: solo se envía en el request.
     */
    @Test
    fun `la contrasenia real nunca llega al DataStore`() {
        val api = apiQueAutentica()
        val archivo = File(tempFolder.root, "sin_password.preferences_pb")
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { archivo },
        )
        val repository = AuthRepositoryImpl(api, SessionPreferences(dataStore))
        val contraseniaSecreta = "codice2026"

        scope.runTest {
            repository.register(nuevoUsuario())

            val valoresGuardados = dataStore.data.first().asMap().values.map { it.toString() }
            assertTrue(valoresGuardados.none { it == contraseniaSecreta })
        }
    }

    /**
     * "Cerrar la app con sesión iniciada y reabrir entra directo" contra
     * AuthRepositoryImpl completo. Se cancela el scope del repositorio
     * original antes de construir el segundo, igual que en
     * [una.codice.core.data.local.SessionPreferencesTest] — así el archivo
     * queda libre para la "nueva instancia" y la simulación de "reabrir la
     * app" es honesta, no solo dos objetos apuntando al mismo DataStore vivo.
     */
    @Test
    fun `una nueva instancia del repositorio ve la sesion ya guardada`() {
        val api = apiQueAutentica()
        val archivo = File(tempFolder.root, "reapertura_repo.preferences_pb")

        val (scopeOriginal, repositorioOriginal) = crearRepositorio(archivo, api)
        lateinit var sesionGuardada: Session
        scopeOriginal.runTest {
            sesionGuardada = (repositorioOriginal.register(nuevoUsuario()) as DomainResult.Success).data
        }
        scopeOriginal.cerrarDelTodo() // "se cierra la app"

        val (scopeAlReabrir, repositorioAlReabrir) = crearRepositorio(archivo, api)
        scopeAlReabrir.runTest {
            val sesionLeida: Session? = repositorioAlReabrir.currentSession().first()
            assertEquals(sesionGuardada, sesionLeida)
        }
    }
}
