package una.codice.settings

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import una.codice.R
import java.util.Locale

/**
 * Los idiomas que ofrece la app. [SYSTEM] sigue el idioma del teléfono: en español muestra español y en inglés
 * muestra inglés (cualquier otro idioma cae al español, que es el que trae la app por defecto).
 *
 * Cada opción se muestra en su propio idioma ("English", "Español") para que se encuentre aunque la app esté en
 * un idioma que la persona no lee.
 */
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    SYSTEM(null, R.string.app_language_system),
    SPANISH("es", R.string.app_language_spanish),
    ENGLISH("en", R.string.app_language_english),
}

/**
 * Ajustes del dispositivo que se leen ANTES de que exista la pantalla: el idioma y el bloqueo con huella.
 *
 * Van en SharedPreferences y no en DataStore a propósito: [wrap] corre en `attachBaseContext`, el primer instante de
 * la Activity, y necesita una lectura síncrona; DataStore solo lee de forma asíncrona. No guarda nada sensible:
 * un idioma y un interruptor.
 */
object AppSettings {
    private const val PREFS = "codice_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_BIOMETRIC_LOCK = "biometric_lock"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun language(context: Context): AppLanguage {
        val saved = prefs(context).getString(KEY_LANGUAGE, null)
        return AppLanguage.entries.firstOrNull { it.tag == saved } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    fun biometricLock(context: Context): Boolean = prefs(context).getBoolean(KEY_BIOMETRIC_LOCK, false)

    fun setBiometricLock(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    /**
     * Envuelve el contexto de la Activity con el idioma elegido; con [AppLanguage.SYSTEM] lo deja como viene.
     *
     * También fija `Locale.getDefault()`: así `uppercase()` y los formatos de fecha siguen el idioma de la app y no
     * el del teléfono. Con SYSTEM se restaura el del teléfono, porque el proceso puede haber tenido otro antes.
     */
    fun wrap(base: Context): Context {
        val systemLocale = base.resources.configuration.locales[0]
        val tag = language(base).tag
        if (tag == null) {
            Locale.setDefault(systemLocale)
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
