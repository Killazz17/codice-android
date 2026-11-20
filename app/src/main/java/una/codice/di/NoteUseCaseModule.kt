package una.codice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import una.codice.core.domain.repository.NoteRepository
import una.codice.core.domain.usecase.note.DeleteNoteUseCase
import una.codice.core.domain.usecase.note.ExtractNoteFromImageUseCase
import una.codice.core.domain.usecase.note.ExtractNoteFromPagesUseCase
import una.codice.core.domain.usecase.note.GetNotesUseCase
import una.codice.core.domain.usecase.note.SearchNotesUseCase

/**
 * Construye los casos de uso de Biblioteca para que Hilt se los pueda
 * entregar a `LibraryViewModel` y `StudentCaptureViewModel`.
 */
@Module
@InstallIn(SingletonComponent::class)
object NoteUseCaseModule {

    @Provides
    fun provideGetNotesUseCase(noteRepository: NoteRepository): GetNotesUseCase =
        GetNotesUseCase(noteRepository)

    @Provides
    fun provideSearchNotesUseCase(): SearchNotesUseCase = SearchNotesUseCase()

    @Provides
    fun provideExtractNoteFromImageUseCase(noteRepository: NoteRepository): ExtractNoteFromImageUseCase =
        ExtractNoteFromImageUseCase(noteRepository)

    @Provides
    fun provideExtractNoteFromPagesUseCase(extractPage: ExtractNoteFromImageUseCase): ExtractNoteFromPagesUseCase =
        ExtractNoteFromPagesUseCase(extractPage)

    @Provides
    fun provideDeleteNoteUseCase(noteRepository: NoteRepository): DeleteNoteUseCase =
        DeleteNoteUseCase(noteRepository)
}
