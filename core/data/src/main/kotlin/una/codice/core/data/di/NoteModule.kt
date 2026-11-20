package una.codice.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import una.codice.core.data.repository.CourseSelectionRepositoryImpl
import una.codice.core.data.repository.NoteRepositoryImpl
import una.codice.core.domain.repository.CourseSelectionRepository
import una.codice.core.domain.repository.NoteRepository

/**
 * Le dice a Hilt qué implementación entregar cuando algo pida un
 * [NoteRepository]. Mismo patrón que `CoverageModule.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoteModule {

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindCourseSelectionRepository(impl: CourseSelectionRepositoryImpl): CourseSelectionRepository
}
