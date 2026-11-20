package una.codice.core.domain.repository

import kotlinx.coroutines.flow.Flow
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.result.DomainResult

/**
 * El contrato del temario de un curso: leerlo, guardarlo como borrador y
 * publicarlo.
 *
 * Vive en `:core:domain` y lo implementa `CourseRepositoryImpl` en
 * `:core:data`, igual que [AuthRepository] o [EnrollmentRepository]. Ver el
 * KDoc de [AuthRepository] para la explicación completa de por qué la
 * dependencia va en ese sentido.
 *
 * ### Por qué guardar y publicar son dos operaciones distintas
 *
 * La spec del producto lo pide explícitamente: *"Nada llega al curso hasta
 * que el profesor lo revisa y publica"*. Si guardar publicara, cada letra que
 * el profesor teclea en un título aparecería al instante en el mapa de
 * cobertura de todos sus estudiantes. Separarlas es lo que permite trabajar
 * el temario a medias sin ensuciar a nadie.
 */
interface CourseRepository {

    /**
     * El temario del curso, como stream: la pantalla se suscribe una vez y
     * reacciona sola a cada guardado, sin tener que volver a preguntar.
     *
     * Emite un [Syllabus] vacío (sin módulos) cuando el curso todavía no
     * tiene temario — nunca `null`, para que la pantalla no tenga que
     * distinguir "no hay" de "no cargó todavía".
     */
    fun observeSyllabus(courseId: String): Flow<Syllabus>

    /**
     * Lee del servidor el temario que el curso tiene hoy, sea publicado o no, y devuelve un [Syllabus] sin módulos
     * si todavía no tiene. Si el profesor aún no tiene borrador de ese curso en el teléfono, lo que llega pasa a
     * ser su borrador; si ya lo tiene, no se pisa.
     *
     * Falla con el motivo si el servidor no responde: a diferencia de [observeSyllabus], aquí "no se pudo leer" no se
     * confunde con "el curso no tiene temario".
     */
    suspend fun loadSyllabus(courseId: String): DomainResult<Syllabus>

    /**
     * Guarda el temario como borrador. No lo publica: [Syllabus.isPublished]
     * se conserva tal como estaba.
     *
     * El borrador vive en el teléfono: nada llega a las tablas del servidor hasta [publish].
     */
    suspend fun saveDraft(syllabus: Syllabus): DomainResult<Unit>

    /**
     * Escribe el borrador del curso en el servidor (módulos, subtemas y objetivos) y lo marca como publicado y visible
     * para los estudiantes matriculados.
     *
     * Publicar dos veces el mismo borrador no duplica nada: lo que ya está en el servidor se reconoce por el número de
     * módulo y el código de subtema, y solo se crea, cambia o quita lo que difiere.
     *
     * Quien llame debe haber validado antes con
     * [una.codice.core.domain.usecase.syllabus.ValidateSyllabusUseCase]: este
     * contrato no revalida, y publicar un temario inconsistente es
     * exactamente lo que rompe la auditoría de apuntes más adelante.
     */
    suspend fun publish(courseId: String): DomainResult<Unit>

    /**
     * [IA-02] Extrae la propuesta del temario enviando un archivo PDF del programa del curso.
     */
    suspend fun extractSyllabusFromPdf(
        courseId: String,
        pdfBytes: ByteArray,
        filename: String = "programa.pdf",
    ): DomainResult<Syllabus>
}
