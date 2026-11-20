package una.codice.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Implementación en memoria de `DataStore<Preferences>`, solo para tests.
 *
 * ### Por qué existe, si ya hay tests contra archivos reales
 *
 * La mayoría de [SessionPreferencesTest] usa [androidx.datastore.preferences.core.PreferenceDataStoreFactory]
 * apuntando a archivos temporales reales — eso es justo lo que hace confiable
 * probar "la sesión sobrevive a reabrir la app". Pero se encontró, de forma
 * reproducible, un problema puntual y ajeno a este proyecto: en este entorno
 * (JVM de test corriendo en Windows), escribir un resultado que deja las
 * Preferences vacías (`clear()`, o quitar todas las claves) sobre un archivo
 * que ya tenía contenido falla al intentar renombrar el archivo temporal
 * interno de DataStore, sin importar si es la misma instancia, una instancia
 * nueva, o si se espera correctamente a que la anterior se cierre. No es un
 * error en [SessionPreferences]: el mismo código funciona sin problema en el
 * emulador (Linux), y las pruebas manuales lo confirman.
 *
 * Para no perder cobertura de "cerrar sesión borra el token" mientras se
 * mantiene la suite corriendo sin depender de un emulador, esta clase evita
 * el sistema de archivos por completo: guarda las Preferences en un
 * `MutableStateFlow` en memoria. Implementa exactamente la misma interfaz que
 * usa [SessionPreferences], así que desde su punto de vista no hay diferencia
 * alguna con el DataStore real.
 */
class FakePreferencesDataStore : DataStore<Preferences> {

    private val estado = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = estado

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val nuevo = transform(estado.value)
        estado.value = nuevo
        return nuevo
    }
}
