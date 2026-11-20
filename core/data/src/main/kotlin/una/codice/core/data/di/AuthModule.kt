package una.codice.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import una.codice.core.data.repository.AuthRepositoryImpl
import una.codice.core.domain.repository.AuthRepository

/**
 * Le dice a Hilt qué implementación entregar cuando algo pida un
 * [AuthRepository].
 *
 * Este es el punto exacto donde se cierra la inversión de dependencias
 * explicada en el KDoc de [AuthRepository]: los casos de uso de
 * `:core:domain` piden la interfaz sin saber nada de `:core:data`; aquí,
 * en `:core:data`, se conecta con la implementación real.
 *
 * ### Por qué `@Binds` y no `@Provides`
 *
 * `@Binds` es la forma preferida cuando ya existe una clase con
 * `@Inject constructor` (como [AuthRepositoryImpl]) y solo hace falta indicar
 * a qué interfaz corresponde: Dagger genera menos código que con `@Provides`,
 * que sí haría falta si construir el objeto necesitara lógica adicional. Por
 * esta razón la función es `abstract` y el parámetro (`impl`) nunca se usa
 * dentro del cuerpo: Dagger lo conecta por firma, no ejecuta la función.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
