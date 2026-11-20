package una.codice.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Construye la única instancia real de `DataStore<Preferences>` que usa la
 * app, y se la entrega a [una.codice.core.data.local.SessionPreferences].
 *
 * Es el único lugar de todo el módulo que toca `Context`. Esa es la idea:
 * [una.codice.core.data.local.SessionPreferences] no sabe nada de Android, así
 * que sus tests no necesitan nada de esto — pueden construir su propio
 * `DataStore<Preferences>` apuntando a un archivo temporal. Ver el KDoc de esa
 * clase para el porqué completo.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("session_preferences") },
        )
}
