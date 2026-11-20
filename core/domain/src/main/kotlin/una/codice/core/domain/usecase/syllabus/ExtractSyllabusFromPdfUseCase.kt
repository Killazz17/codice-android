package una.codice.core.domain.usecase.syllabus

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.result.DomainResult

/**
 * [IA-02] Extrae la propuesta del temario a partir de un programa en archivo PDF.
 */
class ExtractSyllabusFromPdfUseCase(
    private val courseRepository: CourseRepository,
) {

    suspend operator fun invoke(
        courseId: String,
        pdfBytes: ByteArray,
        filename: String = "programa.pdf",
    ): DomainResult<Syllabus> {
        if (pdfBytes.isEmpty()) {
            return DomainResult.Failure(DomainError.Validation(DomainError.Field.PASSWORD, "El archivo PDF está vacío"))
        }
        return courseRepository.extractSyllabusFromPdf(courseId, pdfBytes, filename)
    }
}
