package una.codice.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole

class SessionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    // El JWT se guarda cifrado. Los tests de JVM, sin Keystore, usan TokenCipher.None.
    private val tokenCipher: TokenCipher = TokenCipher.None,
) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val ROLE = stringPreferencesKey("role")
        val USER_NAME = stringPreferencesKey("user_name")
        val EMAIL = stringPreferencesKey("email")
    }

    val session: Flow<Session?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toSession() }

    suspend fun saveSession(session: Session) {
        dataStore.edit { preferences ->
            preferences[Keys.TOKEN] = tokenCipher.encrypt(session.token)
            preferences[Keys.USER_ID] = session.userId
            preferences[Keys.ROLE] = session.role.name
            preferences[Keys.USER_NAME] = session.userName
            preferences[Keys.EMAIL] = session.email
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSession(): Session? {
        // Si el token no se puede descifrar (sesión guardada antes del cifrado, o llave perdida) no hay sesión: se pide entrar de nuevo.
        val token = this[Keys.TOKEN]?.let(tokenCipher::decrypt) ?: return null
        val userId = this[Keys.USER_ID] ?: return null
        val role = this[Keys.ROLE]?.let { valor ->
            runCatching { UserRole.valueOf(valor) }.getOrNull()
        } ?: return null
        val userName = this[Keys.USER_NAME] ?: "Sebastian"
        val email = this[Keys.EMAIL].orEmpty()

        return Session(token = token, userId = userId, role = role, userName = userName, email = email)
    }
}
