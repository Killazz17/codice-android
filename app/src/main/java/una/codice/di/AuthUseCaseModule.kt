package una.codice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.usecase.auth.LoginUseCase
import una.codice.core.domain.usecase.auth.LogoutUseCase
import una.codice.core.domain.usecase.auth.ObserveSessionUseCase
import una.codice.core.domain.usecase.auth.RegisterUseCase
import una.codice.core.domain.usecase.auth.ValidateEmailUseCase
import una.codice.core.domain.usecase.auth.ValidatePasswordUseCase
import una.codice.core.domain.usecase.auth.ChangePasswordUseCase

/**
 * Construye los casos de uso de autenticación para que Hilt se los pueda
 * entregar a cualquier ViewModel que los pida (hoy, `AuthViewModel` de
 * `:feature:auth`).
 *
 * ### Por qué este módulo vive en `:app` y no en `:core:domain`
 *
 * Los casos de uso (`LoginUseCase`, `RegisterUseCase`, etc.) son clases
 * planas, sin `@Inject constructor` ni ninguna anotación de Hilt — a
 * propósito, para que `:core:domain` siga siendo Kotlin puro, sin una sola
 * dependencia de framework. Pero *alguien* tiene que construirlos pasándoles
 * un `AuthRepository` real, y ese repositorio real (`AuthRepositoryImpl`)
 * vive en `:core:data`, un módulo que `:feature:auth` tiene prohibido
 * conocer. `:app` es el único módulo que conoce a los dos a la vez — por
 * eso el puente se arma aquí.
 *
 * Nótese que ninguna función de abajo construye un `AuthRepository`: lo
 * piden como parámetro. Hilt ya sabe resolverlo gracias al `@Binds` que
 * `:core:data` (`AuthModule.kt`) ya declara.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthUseCaseModule {

    @Provides
    fun provideValidateEmailUseCase(): ValidateEmailUseCase = ValidateEmailUseCase()

    @Provides
    fun provideValidatePasswordUseCase(): ValidatePasswordUseCase = ValidatePasswordUseCase()

    @Provides
    fun provideLoginUseCase(
        authRepository: AuthRepository,
        validateEmail: ValidateEmailUseCase,
    ): LoginUseCase = LoginUseCase(authRepository, validateEmail)

    @Provides
    fun provideRegisterUseCase(
        authRepository: AuthRepository,
        validateEmail: ValidateEmailUseCase,
        validatePassword: ValidatePasswordUseCase,
    ): RegisterUseCase = RegisterUseCase(authRepository, validateEmail, validatePassword)

    @Provides
    fun provideObserveSessionUseCase(
        authRepository: AuthRepository,
    ): ObserveSessionUseCase = ObserveSessionUseCase(authRepository)

    @Provides
    fun provideLogoutUseCase(
        authRepository: AuthRepository,
    ): LogoutUseCase = LogoutUseCase(authRepository)

    @Provides
    fun provideChangePasswordUseCase(
        authRepository: AuthRepository,
        validatePassword: ValidatePasswordUseCase,
    ): ChangePasswordUseCase = ChangePasswordUseCase(authRepository, validatePassword)
}
