package una.codice.core.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole

/**
 * Tests de [SessionPreferences] con JUnit puro: sin Robolectric ni emulador.
 *
 * ### Cómo es posible probar DataStore sin Android
 *
 * [SessionPreferences] recibe el `DataStore<Preferences>` ya construido por
 * constructor. Aquí se construye uno propio con
 * [PreferenceDataStoreFactory.create], apuntando a un archivo temporal real en
 * disco (via [TemporaryFolder], la regla de JUnit que crea y borra carpetas de
 * prueba). Es el mismo mecanismo de DataStore que usa la app en producción,
 * solo que apuntando a un archivo distinto — nada aquí es un simulacro.
 *
 * ### Por qué cada "momento" usa su propio `TestScope`, y se cancela
 *
 * DataStore prohíbe que dos instancias estén activas sobre el mismo archivo al
 * mismo tiempo — en un teléfono real esto nunca pasaría, porque solo hay un
 * proceso vivo de la app. Aquí, en cambio, todo corre en la misma JVM, así que
 * hay que simularlo a mano: para pasar de "la app tenía la sesión abierta" a
 * "la app se reabre" (o "el usuario cierra sesión"), primero se CANCELA el
 * scope de la instancia anterior — eso libera el archivo — y solo entonces se
 * crea la siguiente instancia. Sin cancelar, DataStore lanza
 * `IllegalStateException` (si se nota al crear la segunda instancia) o incluso
 * un `IOException` al escribir (si lo nota más tarde, en medio de una
 * escritura) — ambos casos son el mismo problema de fondo, solo detectado en
 * momentos distintos.
 */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher todavía es experimental en esta versión
class SessionPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Crea un "ambiente" nuevo: un scope (que aquí hace las veces de "el
     * proceso de la app") y, sobre él, una [SessionPreferences] lista para
     * usar. Se devuelve el scope junto con el objeto para que el test pueda
     * cancelarlo cuando quiera simular que ese "proceso" terminó.
     */
    private fun crearAmbiente(archivo: File): Pair<TestScope, SessionPreferences> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { archivo })
        return scope to SessionPreferences(dataStore)
    }

    /**
     * "Cierra la app" de verdad: [kotlinx.coroutines.CoroutineScope.cancel]
     * por sí solo solo PIDE la cancelación y devuelve el control de inmediato,
     * sin esperar a que las corrutinas internas de DataStore terminen de
     * liberar el archivo. Esperar con `cancelAndJoin` es lo que hace que la
     * simulación de "reabrir la app" sea confiable: la siguiente instancia no
     * se crea hasta que la anterior liberó el archivo por completo.
     */
    private fun TestScope.cerrarDelTodo() {
        // El job se captura ANTES de entrar a runBlocking: dentro de ese
        // bloque, "coroutineContext" pasaría a referirse al del propio
        // runBlocking (uno nuevo, sin relación), no al de este TestScope.
        val job = coroutineContext.job
        runBlocking { job.cancelAndJoin() }
    }

    @Test
    fun `sin nada guardado no hay sesion`() {
        val (scope, sessionPreferences) = crearAmbiente(File(tempFolder.root, "vacio.preferences_pb"))

        scope.runTest {
            assertNull(sessionPreferences.session.first())
        }
    }

    @Test
    fun `guardar una sesion y leerla devuelve los mismos datos`() {
        val (scope, sessionPreferences) = crearAmbiente(File(tempFolder.root, "basico.preferences_pb"))
        val session = Session(token = "abc123", userId = "user-1", role = UserRole.STUDENT, email = "ana@est.una.ac.cr")

        scope.runTest {
            sessionPreferences.saveSession(session)
            assertEquals(session, sessionPreferences.session.first())
        }
    }

    /**
     * El criterio de aceptación más importante del issue #4, probado tal cual
     * ocurre en la práctica: "cerrar la app y volver a abrirla entra directo".
     *
     * "Cerrar la app" se simula cancelando el scope del primer ambiente antes
     * de crear el segundo — es la parte que hace esta simulación honesta: sin
     * esa cancelación, DataStore ni siquiera dejaría crear el segundo.
     */
    @Test
    fun `una sesion guardada sobrevive a reabrir la app`() {
        val archivo = File(tempFolder.root, "reapertura.preferences_pb")
        val session = Session(token = "token-persistente", userId = "user-7", role = UserRole.TEACHER)

        val (scopeAntesDeCerrar, sessionPreferencesAntesDeCerrar) = crearAmbiente(archivo)
        scopeAntesDeCerrar.runTest {
            sessionPreferencesAntesDeCerrar.saveSession(session)
        }
        scopeAntesDeCerrar.cerrarDelTodo() // "se cierra la app"

        val (scopeAlReabrir, sessionPreferencesAlReabrir) = crearAmbiente(archivo)
        scopeAlReabrir.runTest {
            assertEquals(session, sessionPreferencesAlReabrir.session.first())
        }
    }

    /**
     * Usa [FakePreferencesDataStore] en vez de un archivo real — ver su KDoc
     * para el porqué: escribir un resultado vacío sobre un DataStore de
     * archivo con contenido previo falla de forma reproducible en este
     * entorno de Windows, ajeno al código bajo prueba.
     */
    @Test
    fun `cerrar sesion borra el token y vuelve a emitir null`() = runTest {
        val sessionPreferences = SessionPreferences(FakePreferencesDataStore())
        sessionPreferences.saveSession(Session("t", "u", UserRole.STUDENT))

        sessionPreferences.clear()

        assertNull(sessionPreferences.session.first())
    }

    /**
     * Un rol corrupto (dato que no es "STUDENT" ni "TEACHER", por ejemplo tras
     * una migración futura mal hecha) debe tratarse como "no hay sesión
     * válida", nunca como un crash. Se corrompe el archivo escribiendo
     * directamente en el DataStore subyacente, sin pasar por saveSession(),
     * que jamás produciría un rol inválido por sí sola.
     */
    @Test
    fun `un rol guardado invalido se trata como sesion ausente`() {
        val archivo = File(tempFolder.root, "corrupto.preferences_pb")
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStoreCrudo = PreferenceDataStoreFactory.create(scope = scope, produceFile = { archivo })

        scope.runTest {
            dataStoreCrudo.edit { preferences ->
                preferences[stringPreferencesKey("token")] = "t"
                preferences[stringPreferencesKey("user_id")] = "u"
                preferences[stringPreferencesKey("role")] = "ADMIN" // no existe en UserRole
            }

            assertNull(SessionPreferences(dataStoreCrudo).session.first())
        }
    }

    /**
     * Criterio de aceptación literal: "la contraseña nunca se guarda en
     * DataStore". Se inspeccionan las Preferences crudas (no la Session ya
     * reconstruida, que por diseño ni siquiera tiene ese campo) para
     * comprobar que ninguna clave contiene la contraseña.
     */
    @Test
    fun `la contrasenia nunca queda escrita en el DataStore`() {
        val archivo = File(tempFolder.root, "sin_password.preferences_pb")
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { archivo })

        scope.runTest {
            SessionPreferences(dataStore).saveSession(
                Session(token = "t", userId = "u", role = UserRole.STUDENT),
            )

            val preferencias = dataStore.data.first()
            val valoresGuardados = preferencias.asMap().values.map { it.toString() }
            assertTrue(valoresGuardados.none { it.contains("contraseña", ignoreCase = true) })
            // Y de forma más general: solo deben existir las cinco claves esperadas (token, user_id, role, user_name, email).
            assertEquals(5, preferencias.asMap().size)
        }
    }
}
