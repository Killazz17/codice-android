package una.codice.core.domain.usecase.note

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.Note
import una.codice.core.domain.result.DomainResult

/**
 * Transcribe un apunte de varias páginas: manda cada foto a la IA por separado y en orden, y las junta en un solo
 * borrador con el texto de todas las páginas y los bloques de cada una a continuación de los de la anterior.
 *
 * Las páginas se transcriben una tras otra y no a la vez: así una llamada no compite con la siguiente por la cuota
 * del proveedor, y el primer fallo detiene el resto (no tiene sentido gastar la IA en un apunte que no se puede armar).
 */
class ExtractNoteFromPagesUseCase(
    private val extractPage: ExtractNoteFromImageUseCase,
) {
    suspend operator fun invoke(groupId: String, pages: List<ByteArray>): DomainResult<Note> {
        if (pages.isEmpty()) {
            return DomainResult.Failure(DomainError.Unknown("No hay páginas para transcribir"))
        }

        val transcribed = mutableListOf<Note>()
        pages.forEachIndexed { index, imageBytes ->
            when (val result = extractPage(groupId, imageBytes, filename = "pagina_${index + 1}.jpg")) {
                is DomainResult.Success -> transcribed += result.data
                is DomainResult.Failure -> return DomainResult.Failure(pageError(index, pages.size, result.error))
            }
        }
        return DomainResult.Success(transcribed.singleOrNull() ?: merge(transcribed))
    }

    // Con una sola página el error llega tal cual; con varias se dice cuál falló para que el estudiante sepa cuál repetir.
    private fun pageError(index: Int, total: Int, error: DomainError): DomainError =
        if (total == 1) error else DomainError.Unknown("Página ${index + 1} de $total: ${error.message}")

    private fun merge(pages: List<Note>): Note {
        val first = pages.first()
        return first.copy(
            confirmedText = pages.map { it.confirmedText.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n"),
            pageCount = pages.size,
            transcription = pages.flatMap { it.transcription },
            requiresReview = pages.any { it.requiresReview },
        )
    }
}
