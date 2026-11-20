package una.codice.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import una.codice.core.data.repository.EnrollmentRepositoryImpl
import una.codice.core.domain.repository.EnrollmentRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class EnrollmentModule {

    @Binds
    @Singleton
    abstract fun bindEnrollmentRepository(impl: EnrollmentRepositoryImpl): EnrollmentRepository
}
