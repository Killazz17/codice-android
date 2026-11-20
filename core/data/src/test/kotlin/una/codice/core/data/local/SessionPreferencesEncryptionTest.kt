package una.codice.core.data.local

import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole

/** El JWT nunca queda en el disco en claro, y una sesión que no se puede descifrar se trata como "sin sesión". */
class SessionPreferencesEncryptionTest {

    /** Un "cifrado" reversible y distinto del texto, para comprobar que se usa sin necesitar el Keystore. */
    private class ReverseCipher(private val canDecrypt: Boolean = true) : TokenCipher {
        override fun encrypt(plain: String): String = "enc:" + plain.reversed()
        override fun decrypt(encoded: String): String? =
            if (canDecrypt && encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
    }

    private val session = Session(token = "eyJ.header.firma", userId = "7", role = UserRole.TEACHER, userName = "Ana")

    @Test
    fun `el token se guarda cifrado y se lee de vuelta igual`() = runTest {
        val dataStore = FakePreferencesDataStore()
        val preferences = SessionPreferences(dataStore, ReverseCipher())

        preferences.saveSession(session)

        val stored = dataStore.data.first()[stringPreferencesKey("token")]
        assertNotEquals(session.token, stored)
        assertEquals(session, preferences.session.first())
    }

    @Test
    fun `un token que no se puede descifrar deja la app sin sesion`() = runTest {
        val dataStore = FakePreferencesDataStore()
        SessionPreferences(dataStore, ReverseCipher()).saveSession(session)

        // Por ejemplo, una llave del Keystore que ya no existe.
        assertNull(SessionPreferences(dataStore, ReverseCipher(canDecrypt = false)).session.first())
    }

    @Test
    fun `una sesion guardada en claro por una version anterior pide iniciar sesion otra vez`() = runTest {
        val dataStore = FakePreferencesDataStore()
        SessionPreferences(dataStore, TokenCipher.None).saveSession(session)

        assertNull(SessionPreferences(dataStore, ReverseCipher()).session.first())
    }
}
