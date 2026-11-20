package una.codice.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import una.codice.core.data.local.KeystoreTokenCipher
import una.codice.core.data.local.TokenCipher

/** Cifrado del token de la sesión: el JWT se guarda con una llave del Keystore de Android. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    abstract fun bindTokenCipher(impl: KeystoreTokenCipher): TokenCipher
}
