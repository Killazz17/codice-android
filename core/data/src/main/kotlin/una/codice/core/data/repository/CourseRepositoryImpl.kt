package una.codice.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import una.codice.core.data.network.apiCall
import una.codice.core.data.network.syllabus.ModuleCreateRequestDto
import una.codice.core.data.network.syllabus.ModuleDto
import una.codice.core.data.network.syllabus.ModuleOrderRequestDto
import una.codice.core.data.network.syllabus.ModuleUpdateRequestDto
import una.codice.core.data.network.syllabus.ObjectiveDto
import una.codice.core.data.network.syllabus.ObjectiveRequestDto
import una.codice.core.data.network.syllabus.SubtopicCreateRequestDto
import una.codice.core.data.network.syllabus.SubtopicDto
import una.codice.core.data.network.syllabus.SubtopicUpdateRequestDto
import una.codice.core.data.network.syllabus.SyllabusApiService
import una.codice.core.data.network.syllabus.SyllabusDto
import una.codice.core.data.network.syllabus.toDomain
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.model.SyllabusSource
import una.codice.core.domain.repository.CourseRepository
import una.codice.core.domain.result.DomainResult

/**
 * [CourseRepository] contra el backend de Códice.
 *
 * ### El borrador vive en el teléfono; el servidor solo ve lo que se publica
 *
 * El profesor revisa y corrige el temario antes de publicarlo, así que el borrador ([saveDraft]) se guarda aquí, en
 * memoria, y nada llega a las tablas del servidor hasta [publish]. Publicar escribe módulos, subtemas y objetivos con
 * los endpoints del temario y después lo marca como publicado. La salida cruda de la IA, en cambio, la guarda el
 * servidor al extraer el PDF y publicar no la toca.
 *
 * ### Publicar dos veces no duplica nada
 *
 * Lo que ya está en el servidor se reconoce por su id (un borrador que viene del servidor lo guarda) y, si no, por el
 * número del módulo y el código del subtema (únicos en su nivel); solo se crea, cambia o quita lo que difiere del
 * borrador. Renombrar, mover y quitar módulos usa `PATCH`, `PUT .../orden` y `DELETE` de `/modulos`.
 */
@Singleton
class CourseRepositoryImpl @Inject constructor(
    private val teacherApiService: TeacherApiService,
    private val syllabusApiService: SyllabusApiService,
) : CourseRepository {

    private val drafts = MutableStateFlow<Map<String, Syllabus>>(emptyMap())

    // Solo el borrador de ESE curso: sin temarios de relleno ni "el más parecido", que podrían mostrar el de otro curso.
    override fun observeSyllabus(courseId: String): Flow<Syllabus> =
        drafts.map { it[courseId] ?: Syllabus(courseId = courseId) }.distinctUntilChanged()

    override suspend fun loadSyllabus(courseId: String): DomainResult<Syllabus> {
        val id = courseId.toLongOrNull() ?: return unknownCourse()
        return when (val result = apiCall(onHttpError = ::syllabusError) { syllabusApiService.getSyllabus(id).toDomain() }) {
            is DomainResult.Success -> {
                // Un borrador que el profesor ya empezó no se pisa con lo que hay en el servidor.
                // `update` hace la comprobación y la escritura de una sola vez: un `saveDraft` que llegue en medio no se pierde.
                drafts.update { if (courseId in it) it else it + (courseId to result.data) }
                result
            }
            is DomainResult.Failure -> result
        }
    }

    override suspend fun saveDraft(syllabus: Syllabus): DomainResult<Unit> {
        drafts.update { current ->
            val anterior = current[syllabus.courseId]
            current + (syllabus.courseId to syllabus.copy(isPublished = anterior?.isPublished ?: false))
        }
        return DomainResult.Success(Unit)
    }

    override suspend fun publish(courseId: String): DomainResult<Unit> {
        val id = courseId.toLongOrNull() ?: return unknownCourse()
        val draft = drafts.value[courseId]
            ?: return DomainResult.Failure(DomainError.Unknown("No hay un temario para publicar"))

        val server = when (val result = apiCall(onHttpError = ::syllabusError) { syllabusApiService.getSyllabus(id) }) {
            is DomainResult.Success -> result.data
            is DomainResult.Failure -> return result
        }

        val synced = syncWithServer(id, draft, server)
        if (synced is DomainResult.Failure) return synced

        // El servidor es ahora la fuente de verdad: el borrador pasa a ser lo publicado, con los ids del servidor.
        val refreshed = apiCall(onHttpError = ::syllabusError) { syllabusApiService.getSyllabus(id).toDomain() }
        val published = when (refreshed) {
            is DomainResult.Success -> refreshed.data
            is DomainResult.Failure -> draft.copy(isPublished = true)
        }
        drafts.update { it + (courseId to published) }
        return DomainResult.Success(Unit)
    }

    override suspend fun extractSyllabusFromPdf(
        courseId: String,
        pdfBytes: ByteArray,
        filename: String,
    ): DomainResult<Syllabus> {
        // El id del curso viene del backend y es numérico. Sin un id real no hay a quién pedirle el temario: no se inventa uno.
        val courseIdLong = courseId.toLongOrNull() ?: return unknownCourse()
        val requestFile = pdfBytes.toRequestBody("application/pdf".toMediaTypeOrNull())
        val bodyPart = MultipartBody.Part.createFormData("file", filename, requestFile)

        // Si la IA falla, el error llega a la pantalla (que ofrece cargar el temario a mano): no se arma un temario de relleno.
        val apiResult = apiCall(onHttpError = ::extractionError) {
            teacherApiService.extractSyllabusFromPdf(courseIdLong, bodyPart)
        }
        return when (apiResult) {
            is DomainResult.Success -> {
                val syllabus = Syllabus(
                    courseId = courseId,
                    modules = apiResult.data.modules.map { m ->
                        CourseModule(
                            id = "mod-${m.number}",
                            number = m.number,
                            title = m.title,
                            subtopics = m.subtopics.mapIndexed { idx, s ->
                                Subtopic(
                                    id = "sub-${m.number}-${s.code}",
                                    code = s.code,
                                    title = s.title,
                                    position = idx,
                                    objectives = s.objectives,
                                    confidence = s.confidence.toFloat(),
                                )
                            },
                            confidence = m.confidence.toFloat(),
                        )
                    },
                    isPublished = false,
                    source = SyllabusSource.AI_EXTRACTED,
                )
                saveDraft(syllabus)
                DomainResult.Success(syllabus)
            }
            is DomainResult.Failure -> DomainResult.Failure(apiResult.error)
        }
    }

    /** Un módulo del borrador y el del servidor que le corresponde (`null` si es nuevo). */
    private data class ModuleMatch(val draft: CourseModule, val server: ModuleDto?)

    /**
     * Qué módulo del servidor es cada módulo del borrador. Primero por id (un borrador que viene del servidor guarda los
     * ids reales, así que mover o renombrar un módulo no lo confunde con otro); si no, por número, que es como se
     * reconocía un temario nuevo de la IA sobre uno ya publicado. Lo que el borrador ya no tiene es lo que se quita.
     */
    private fun matchModules(draft: List<CourseModule>, server: SyllabusDto): Pair<List<ModuleMatch>, List<ModuleDto>> {
        val claimed = mutableSetOf<Long>()
        val byId = server.modules.associateBy { it.id }
        val first = draft.map { module -> ModuleMatch(module, byId[module.id.toLongOrNull()]?.also { claimed += it.id }) }
        val matches = first.map { match ->
            if (match.server != null) {
                match
            } else {
                val byNumber = server.modules.firstOrNull { it.number == match.draft.number && it.id !in claimed }
                byNumber?.let { claimed += it.id }
                ModuleMatch(match.draft, byNumber)
            }
        }
        return matches to server.modules.filter { it.id !in claimed }
    }

    /** Lo mismo para los subtemas de un módulo: por id y, si no, por código. */
    private fun matchSubtopics(draft: List<Subtopic>, server: List<SubtopicDto>): Pair<List<Pair<Subtopic, SubtopicDto?>>, List<SubtopicDto>> {
        val claimed = mutableSetOf<Long>()
        val byId = server.associateBy { it.id }
        val first = draft.map { subtopic -> subtopic to byId[subtopic.id.toLongOrNull()]?.also { claimed += it.id } }
        val matches = first.map { (subtopic, found) ->
            if (found != null) {
                subtopic to found
            } else {
                val byCode = server.firstOrNull { it.code == subtopic.code.trim() && it.id !in claimed }
                byCode?.let { claimed += it.id }
                subtopic to byCode
            }
        }
        return matches to server.filter { it.id !in claimed }
    }

    private suspend fun syncWithServer(courseId: Long, draft: Syllabus, server: SyllabusDto): DomainResult<Unit> {
        val draftModules = draft.modules.sortedBy { it.number }
        val (matches, removedModules) = matchModules(draftModules, server)
        val subtopicPlans = matches.map { match ->
            match.server?.let { matchSubtopics(match.draft.subtopics, it.subtopics) }
                ?: (match.draft.subtopics.map { it to null } to emptyList())
        }

        // 1. Primero lo que se quita: si un subtema o un módulo ya tiene apuntes el servidor lo rechaza, y mejor enterarse
        // antes de haber escrito nada más.
        for ((_, gone) in subtopicPlans) {
            for (subtopic in gone) {
                apiCall(onHttpError = { removeSubtopicError(subtopic.code, it) }) {
                    syllabusApiService.deleteSubtopic(subtopic.id)
                }.orElse { return it }
            }
        }
        for (gone in removedModules) {
            apiCall(onHttpError = { removeModuleError(gone.number, it) }) {
                syllabusApiService.deleteModule(gone.id)
            }.orElse { return it }
        }

        // 2. Después lo que se crea o cambia, módulo por módulo. Un módulo nuevo toma el número del borrador si está libre;
        // si no, uno por encima de todos, y el orden final se fija en el paso 3.
        val takenNumbers = matches.mapNotNull { it.server?.number }.toMutableSet()
        var highest = maxOf(server.modules.maxOfOrNull { it.number } ?: 0, draftModules.maxOfOrNull { it.number } ?: 0)
        val currentNumbers = mutableListOf<Int>()
        val moduleIds = mutableListOf<Long>()
        matches.forEachIndexed { index, match ->
            val module = match.draft
            val title = module.title.trim()
            val serverModule = match.server
            val moduleId: Long
            if (serverModule == null) {
                val number = if (module.number in takenNumbers) ++highest else module.number
                takenNumbers += number
                moduleId = apiCall(onHttpError = ::writeError) {
                    syllabusApiService.createModule(courseId, ModuleCreateRequestDto(number, title)).id
                }.orElse { return it }
                currentNumbers += number
            } else {
                moduleId = serverModule.id
                currentNumbers += serverModule.number
                if (title != serverModule.title.trim()) {
                    apiCall(onHttpError = ::writeError) {
                        syllabusApiService.updateModule(moduleId, ModuleUpdateRequestDto(title = title))
                    }.orElse { return it }
                }
            }
            moduleIds += moduleId

            for ((subtopic, existing) in subtopicPlans[index].first.sortedBy { it.first.position }) {
                val synced = syncSubtopic(moduleId, subtopic, existing)
                if (synced is DomainResult.Failure) return synced
            }
        }

        // 3. El orden: si los números del servidor no son los del borrador (se movió un módulo o se creó uno en medio), un
        // solo PUT con todos los ids en el orden del borrador los renumera sin chocar con la unicidad.
        if (currentNumbers != draftModules.map { it.number }) {
            apiCall(onHttpError = ::reorderError) {
                syllabusApiService.reorderModules(courseId, ModuleOrderRequestDto(moduleIds))
            }.orElse { return it }
        }

        // 4. Al final, publicar: el servidor exige al menos un subtema.
        apiCall(onHttpError = ::publishError) { syllabusApiService.publish(courseId) }.orElse { return it }
        return DomainResult.Success(Unit)
    }

    private suspend fun syncSubtopic(moduleId: Long, subtopic: Subtopic, existing: SubtopicDto?): DomainResult<Unit> {
        val code = subtopic.code.trim()
        val title = subtopic.title.trim()

        val subtopicId: Long
        val serverObjectives: List<ObjectiveDto>
        if (existing == null) {
            val created = apiCall(onHttpError = ::writeError) {
                syllabusApiService.createSubtopic(moduleId, SubtopicCreateRequestDto(code, title, subtopic.position))
            }.orElse { return it }
            subtopicId = created.id
            serverObjectives = created.objectives
        } else {
            subtopicId = existing.id
            serverObjectives = existing.objectives
            // El código cambia cuando se movió su módulo (el prefijo sigue al número del módulo).
            val changedCode = code.takeIf { it != existing.code }
            val changedTitle = title.takeIf { it != existing.title }
            val changedPosition = subtopic.position.takeIf { it != existing.position }
            if (changedCode != null || changedTitle != null || changedPosition != null) {
                apiCall(onHttpError = ::writeError) {
                    syllabusApiService.updateSubtopic(
                        subtopicId,
                        SubtopicUpdateRequestDto(code = changedCode, title = changedTitle, position = changedPosition),
                    )
                }.orElse { return it }
            }
        }
        return syncObjectives(subtopicId, subtopic.objectives, serverObjectives)
    }

    // Los objetivos no tienen identidad propia ni orden en el servidor: se quitan los que sobran y se agregan los que faltan.
    private suspend fun syncObjectives(
        subtopicId: Long,
        objectives: List<String>,
        onServer: List<ObjectiveDto>,
    ): DomainResult<Unit> {
        val wanted = objectives.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        for (extra in onServer.filter { it.description !in wanted }) {
            val objectiveId = extra.id ?: continue
            apiCall(onHttpError = ::writeError) { syllabusApiService.deleteObjective(objectiveId) }.orElse { return it }
        }
        val present = onServer.map { it.description }.toSet()
        for (missing in wanted.filter { it !in present }) {
            apiCall(onHttpError = ::writeError) {
                syllabusApiService.addObjective(subtopicId, ObjectiveRequestDto(missing))
            }.orElse { return it }
        }
        return DomainResult.Success(Unit)
    }

    private fun unknownCourse(): DomainResult.Failure =
        DomainResult.Failure(DomainError.Unknown("No se pudo identificar el curso del temario"))

    private fun extractionError(code: Int): DomainError? = when (code) {
        400 -> DomainError.Unknown("El archivo debe ser un PDF válido")
        403 -> DomainError.Unknown("Solo el profesor dueño del curso puede cargar su temario")
        413 -> DomainError.Unknown("El PDF es muy pesado (máximo 10 MB)")
        503 -> DomainError.Unknown("La IA no está disponible en este momento")
        else -> null
    }

    private fun syllabusError(code: Int): DomainError? = when (code) {
        401 -> DomainError.Unauthorized()
        403 -> DomainError.Unknown("Solo el profesor dueño del curso puede ver o publicar su temario")
        404 -> DomainError.Unknown("No se encontró el curso en el servidor")
        else -> null
    }

    private fun writeError(code: Int): DomainError? = when (code) {
        400 -> DomainError.Unknown(
            "El servidor rechazó un dato del temario: revisa que ningún título (máximo 255 caracteres), " +
                "código (máximo 20) u objetivo (máximo 500) pase del límite",
        )
        409 -> DomainError.Unknown(
            "El temario cambió en el servidor mientras lo editabas. Vuelve a abrir el curso y revisa los cambios",
        )
        else -> syllabusError(code)
    }

    private fun removeSubtopicError(subtopicCode: String, code: Int): DomainError? = when (code) {
        422 -> DomainError.Unknown(
            "El subtema $subtopicCode ya tiene apuntes de estudiantes y no se puede quitar del temario",
        )
        else -> writeError(code)
    }

    private fun removeModuleError(moduleNumber: Int, code: Int): DomainError? = when (code) {
        422 -> DomainError.Unknown(
            "El módulo $moduleNumber tiene subtemas con apuntes de estudiantes y no se puede quitar del temario",
        )
        else -> writeError(code)
    }

    private fun reorderError(code: Int): DomainError? = when (code) {
        422 -> DomainError.Unknown(
            "El orden de los módulos no coincide con el del servidor. Vuelve a abrir el curso y revisa los cambios",
        )
        else -> writeError(code)
    }

    private fun publishError(code: Int): DomainError? = when (code) {
        422 -> DomainError.Unknown("No se puede publicar un temario sin subtemas")
        else -> writeError(code)
    }
}

/** El valor si salió bien; si no, [onFailure] decide (normalmente un `return` de quien llama). */
private inline fun <T> DomainResult<T>.orElse(onFailure: (DomainResult.Failure) -> Nothing): T = when (this) {
    is DomainResult.Success -> data
    is DomainResult.Failure -> onFailure(this)
}
