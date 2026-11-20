package una.codice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.usecase.enrollment.ObserveMyEnrollmentsUseCase
import una.codice.core.domain.usecase.enrollment.ObserveTeacherGroupsUseCase
import una.codice.core.domain.usecase.enrollment.RequestJoinUseCase
import una.codice.core.domain.usecase.enrollment.ResolveInviteCodeUseCase

/**
 * Provee los casos de uso de matriculación.
 *
 * Sigue el mismo patrón que [AuthUseCaseModule]: se inyecta la interfaz del
 * repositorio y se construyen los casos de uso.
 */
@Module
@InstallIn(SingletonComponent::class)
object EnrollmentUseCaseModule {

    @Provides
    fun provideResolveInviteCodeUseCase(
        enrollmentRepository: EnrollmentRepository
    ): ResolveInviteCodeUseCase = ResolveInviteCodeUseCase(enrollmentRepository)

    @Provides
    fun provideRequestJoinUseCase(
        enrollmentRepository: EnrollmentRepository
    ): RequestJoinUseCase = RequestJoinUseCase(enrollmentRepository)

    @Provides
    fun provideObserveMyEnrollmentsUseCase(
        enrollmentRepository: EnrollmentRepository
    ): ObserveMyEnrollmentsUseCase = ObserveMyEnrollmentsUseCase(enrollmentRepository)

    @Provides
    fun provideObserveTeacherGroupsUseCase(
        authRepository: AuthRepository,
        enrollmentRepository: EnrollmentRepository
    ): ObserveTeacherGroupsUseCase = ObserveTeacherGroupsUseCase(authRepository, enrollmentRepository)
}
