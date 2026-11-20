package una.codice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.usecase.syllabus.ExtractSyllabusFromPdfUseCase
import una.codice.core.domain.usecase.syllabus.LoadSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.ObserveSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.PublishSyllabusUseCase
import una.codice.core.domain.usecase.syllabus.SaveSyllabusDraftUseCase
import una.codice.core.domain.usecase.syllabus.ValidateSyllabusUseCase

/**
 * Construye los casos de uso del temario para que Hilt se los entregue a
 * `SyllabusViewModel` (`:feature:syllabus`, issue #32).
 *
 * Mismo motivo que `AuthUseCaseModule` y `CoverageUseCaseModule` — ver el
 * KDoc del primero: los casos de uso son clases planas sin `@Inject`, y
 * quien las construye necesita un [CourseRepository] real, que solo
 * `:core:data` implementa y que `:feature:syllabus` tiene prohibido conocer.
 * `:app` es el único módulo que conoce a los dos.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyllabusUseCaseModule {

    @Provides
    fun provideValidateSyllabusUseCase(): ValidateSyllabusUseCase = ValidateSyllabusUseCase()

    @Provides
    fun provideObserveSyllabusUseCase(
        courseRepository: CourseRepository,
    ): ObserveSyllabusUseCase = ObserveSyllabusUseCase(courseRepository)

    @Provides
    fun provideLoadSyllabusUseCase(
        courseRepository: CourseRepository,
    ): LoadSyllabusUseCase = LoadSyllabusUseCase(courseRepository)

    @Provides
    fun provideSaveSyllabusDraftUseCase(
        courseRepository: CourseRepository,
    ): SaveSyllabusDraftUseCase = SaveSyllabusDraftUseCase(courseRepository)

    @Provides
    fun providePublishSyllabusUseCase(
        courseRepository: CourseRepository,
        validateSyllabus: ValidateSyllabusUseCase,
    ): PublishSyllabusUseCase = PublishSyllabusUseCase(courseRepository, validateSyllabus)

    @Provides
    fun provideExtractSyllabusFromPdfUseCase(
        courseRepository: CourseRepository,
    ): ExtractSyllabusFromPdfUseCase = ExtractSyllabusFromPdfUseCase(courseRepository)
}
