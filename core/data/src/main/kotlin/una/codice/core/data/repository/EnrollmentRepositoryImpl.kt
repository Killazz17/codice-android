package una.codice.core.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import una.codice.core.data.network.apiCall
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.enrollment.EnrollmentRequestDto
import una.codice.core.data.network.enrollment.toDomain
import una.codice.core.data.network.teacher.CourseCreateDto
import una.codice.core.data.network.teacher.EnrollmentStatusDto
import una.codice.core.data.network.teacher.GroupCreateDto
import una.codice.core.data.network.teacher.GroupSettingsDto
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.data.network.teacher.TeacherGroupDto
import una.codice.core.data.network.teacher.toDomain
import una.codice.core.data.network.teacher.toGroupStudent
import una.codice.core.data.network.teacher.toInviteInfo
import una.codice.core.data.network.teacher.toStudentGroup
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.*
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.result.DomainResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [EnrollmentRepository] contra el backend de Códice.
 *
 * - Estudiante: buscar el grupo por código, unirse y ver sus matrículas.
 * - Profesor: sus grupos con métricas, el código de invitación, las solicitudes del grupo y crear
 *   cursos. Cada cambio incrementa [teacherVersion] para que las pantallas abiertas se vuelvan a pedir.
 *
 * Los `Flow` emiten una lista vacía si la llamada falla: el contrato no modela errores en ellos
 * (igual que `NoteRepositoryImpl.observeNotes`).
 */
@Singleton
class EnrollmentRepositoryImpl @Inject constructor(
    private val enrollmentApiService: EnrollmentApiService,
    private val teacherApiService: TeacherApiService,
) : EnrollmentRepository {

    /** Cada solicitud de matrícula exitosa lo incrementa para que "Mis cursos" se vuelva a pedir. */
    private val enrollmentsVersion = MutableStateFlow(0)

    /** Cada cambio del profesor (crear, aprobar, regenerar, configurar) vuelve a pedir sus datos. */
    private val teacherVersion = MutableStateFlow(0)

    // ---------- Estudiante ----------

    override suspend fun resolveInviteCode(code: String): DomainResult<StudentGroup> =
        apiCall(onHttpError = { httpCode ->
            when (httpCode) {
                404 -> DomainError.Validation(DomainError.Field.CODE, "No existe un grupo con ese código")
                401 -> DomainError.Unauthorized()
                else -> null
            }
        }) {
            enrollmentApiService.findGroupByInviteCode(code).toDomain()
        }

    /**
     * El `studentId` no viaja: el backend matricula al dueño del token. Si el grupo no pide
     * aprobación la matrícula queda activa de una vez; si la pide, queda pendiente.
     */
    override suspend fun requestJoin(code: String, studentId: String): DomainResult<Unit> {
        val group = when (val result = resolveInviteCode(code)) {
            is DomainResult.Success -> result.data
            is DomainResult.Failure -> return result
        }
        val result = apiCall(onHttpError = { httpCode ->
            when (httpCode) {
                409 -> DomainError.Validation(DomainError.Field.CODE, "Ya tienes una matrícula en este curso")
                403 -> DomainError.Validation(DomainError.Field.CODE, "Solo un estudiante puede unirse a un curso")
                401 -> DomainError.Unauthorized()
                else -> null
            }
        }) {
            enrollmentApiService.requestEnrollment(EnrollmentRequestDto(groupId = group.id.toLong()))
            Unit
        }
        if (result is DomainResult.Success) enrollmentsVersion.value++
        return result
    }

    /** Pide las matrículas al suscribirse y otra vez después de cada [requestJoin] exitoso. */
    override fun observeMyEnrollments(): Flow<List<Enrollment>> = enrollmentsVersion.map {
        orEmpty { enrollmentApiService.getMyEnrollments().map { it.toDomain() } }
    }

    /** Al salir, "Mis cursos" y los selectores de curso se vuelven a pedir: el curso desaparece de inmediato. */
    override suspend fun leaveEnrollment(enrollmentId: String): DomainResult<Unit> {
        val result = apiCall(onHttpError = { httpCode ->
            when (httpCode) {
                401 -> DomainError.Unauthorized()
                403 -> DomainError.Unknown("Solo puedes salir de tus propios cursos")
                404, 409 -> DomainError.Unknown("Ya no estás inscrito en ese curso")
                else -> null
            }
        }) {
            enrollmentApiService.leaveEnrollment(enrollmentId.toLong())
        }
        if (result is DomainResult.Success) enrollmentsVersion.value++
        return result
    }

    // ---------- Profesor ----------

    /**
     * Los grupos de todos los cursos del profesor, con sus métricas: inscritos activos, solicitudes
     * pendientes y el promedio de cobertura del grupo (agregado, de 0 a 1). El `teacherId` no viaja:
     * `GET /courses` ya devuelve solo los cursos del dueño del token.
     */
    override fun observeTeacherGroups(teacherId: String): Flow<List<TeacherGroupSummary>> =
        teacherVersion.map {
            orEmpty {
                coroutineScope {
                    loadTeacherGroups().map { group -> async { summarize(group) } }.awaitAll()
                }
            }
        }

    override fun observeGroupInviteInfo(groupId: String): Flow<GroupInviteInfo> =
        teacherVersion.map {
            val group = orEmpty { loadTeacherGroups() }.firstOrNull { it.id.toString() == groupId }
            // Sin grupo (sin cursos o sin conexión) se emite uno vacío para que la pantalla no se quede cargando.
            group?.toInviteInfo() ?: GroupInviteInfo(groupId = groupId, inviteCode = "")
        }

    override suspend fun regenerateInviteCode(groupId: String): DomainResult<GroupInviteInfo> =
        teacherCall { teacherApiService.regenerateInviteCode(groupId.toLong()).toInviteInfo() }

    override suspend fun updateInviteSettings(groupId: String, requiresApproval: Boolean): DomainResult<Unit> =
        teacherCall {
            teacherApiService.updateGroupSettings(groupId.toLong(), GroupSettingsDto(requiresApproval))
            Unit
        }

    /** Pendientes e inscritos; las solicitudes rechazadas no se muestran. */
    override fun observeGroupStudents(groupId: String): Flow<List<GroupStudent>> =
        teacherVersion.map {
            orEmpty { teacherApiService.getGroupEnrollments(groupId.toLong()).map { it.toGroupStudent() } }
                .filter { it.status != EnrollmentStatus.REJECTED }
        }

    override suspend fun decideEnrollment(enrollmentId: String, approve: Boolean): DomainResult<Unit> =
        teacherCall(on409 = "La solicitud ya fue decidida") {
            val status = if (approve) EnrollmentStatus.ACTIVE else EnrollmentStatus.REJECTED
            teacherApiService.decideEnrollment(enrollmentId.toLong(), EnrollmentStatusDto(status.name))
        }

    override suspend fun removeStudent(groupId: String, enrollmentId: String): DomainResult<Unit> =
        teacherCall(on409 = "Ese estudiante ya no está inscrito en el grupo") {
            teacherApiService.removeStudent(groupId.toLong(), enrollmentId.toLong())
        }

    /**
     * Si el profesor ya tiene un curso con ese código, el grupo se agrega a ese curso (así "Crear curso" con un código
     * repetido ya no es un callejón sin salida). Si no, son dos llamadas: el curso y después su primer grupo, que es el
     * que trae el código de invitación. El código es único entre todos los profesores: si existe y no está en
     * `GET /courses` (que solo trae los del dueño del token), es de otro profesor y el backend responde 409.
     */
    override suspend fun createCourse(newCourse: NewCourse): DomainResult<GroupInviteInfo> {
        val existing = orNull { teacherApiService.getMyCourses() }
            ?.firstOrNull { it.code.equals(newCourse.code, ignoreCase = true) }
        if (existing != null) {
            return addGroup(
                existing.id.toString(),
                NewGroup(newCourse.groupNumber, newCourse.period, newCourse.requiresApproval),
            )
        }
        return teacherCall(on409 = "Ese código de curso ya lo usa otro profesor") {
            val course = teacherApiService.createCourse(CourseCreateDto(code = newCourse.code, name = newCourse.name))
            teacherApiService.createGroup(
                GroupCreateDto(
                    courseId = course.id,
                    number = newCourse.groupNumber,
                    period = newCourse.period,
                    requiresApproval = newCourse.requiresApproval,
                ),
            ).toInviteInfo()
        }
    }

    override suspend fun addGroup(courseId: String, newGroup: NewGroup): DomainResult<GroupInviteInfo> =
        teacherCall(on409 = "Ese curso ya tiene un grupo ${newGroup.groupNumber}") {
            teacherApiService.createGroup(
                GroupCreateDto(
                    courseId = courseId.toLong(),
                    number = newGroup.groupNumber,
                    period = newGroup.period,
                    requiresApproval = newGroup.requiresApproval,
                ),
            ).toInviteInfo()
        }

    override suspend fun deleteCourse(courseId: String): DomainResult<Unit> =
        teacherCall(on422 = "No se puede eliminar un curso con estudiantes matriculados") {
            teacherApiService.deleteCourse(courseId.toLong())
        }

    private suspend fun loadTeacherGroups(): List<TeacherGroupDto> = coroutineScope {
        teacherApiService.getMyCourses()
            .map { course -> async { teacherApiService.getCourseGroups(course.id) } }
            .awaitAll()
            .flatten()
    }

    /** Si una métrica falla, el grupo se muestra igual con esa métrica en cero. */
    private suspend fun summarize(group: TeacherGroupDto): TeacherGroupSummary = coroutineScope {
        val enrollments = async { orEmpty { teacherApiService.getGroupEnrollments(group.id) } }
        val coverage = async { orNull { teacherApiService.getGroupCoverage(group.id).toDomain() } }
        val list = enrollments.await()
        TeacherGroupSummary(
            group = group.toStudentGroup(),
            activeStudentCount = list.count { it.status == EnrollmentStatus.ACTIVE.name },
            averageCoverage = ((coverage.await()?.averageCoveredPercentage ?: 0.0) / 100.0).toFloat(),
            pendingRequestsCount = list.count { it.status == EnrollmentStatus.PENDING.name },
        )
    }

    /** Una acción del profesor: traduce los errores y, si sale bien, refresca las pantallas abiertas. */
    private suspend fun <T> teacherCall(on409: String? = null, on422: String? = null, block: suspend () -> T): DomainResult<T> {
        val result = apiCall(
            onHttpError = { httpCode ->
                when (httpCode) {
                    401 -> DomainError.Unauthorized()
                    403 -> DomainError.Unknown("Solo el profesor del grupo puede hacer esto")
                    409 -> on409?.let { DomainError.Unknown(it) }
                    422 -> on422?.let { DomainError.Unknown(it) }
                    else -> null
                }
            },
            block = block,
        )
        if (result is DomainResult.Success) teacherVersion.value++
        return result
    }

    private suspend fun <T> orEmpty(block: suspend () -> List<T>): List<T> = orNull(block) ?: emptyList()

    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error // nunca atrapar la cancelación de la corrutina, ver ApiCall.kt
    } catch (error: Exception) {
        null
    }
}
