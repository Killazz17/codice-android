package una.codice.core.domain.repository

import kotlinx.coroutines.flow.Flow
import una.codice.core.domain.model.*
import una.codice.core.domain.result.DomainResult

/**
 * Contrato para la gestión de matrículas y grupos.
 *
 * Define cómo la aplicación interactúa con los datos de inscripción sin
 * revelar si vienen de una base de datos local, una API o un mock.
 */
interface EnrollmentRepository {

    /**
     * Busca el grupo asociado a un código de invitación.
     * Devuelve error si el código no existe o expiró.
     */
    suspend fun resolveInviteCode(code: String): DomainResult<StudentGroup>

    /**
     * Crea una solicitud de unión a un grupo usando el código.
     * La matrícula puede quedar en PENDING o ACTIVE según el grupo.
     */
    suspend fun requestJoin(code: String, studentId: String): DomainResult<Unit>

    /**
     * Observa la lista de todas las matrículas del estudiante actual.
     */
    fun observeMyEnrollments(): Flow<List<Enrollment>>

    /**
     * Observa la lista de grupos que imparte el profesor actual.
     * Incluye métricas de cobertura y solicitudes pendientes.
     */
    fun observeTeacherGroups(teacherId: String): Flow<List<TeacherGroupSummary>>

    /**
     * Observa la información de invitación de un grupo específico.
     */
    fun observeGroupInviteInfo(groupId: String): Flow<GroupInviteInfo>

    /**
     * Regenera el código de invitación de un grupo.
     * Invalida el código anterior.
     */
    suspend fun regenerateInviteCode(groupId: String): DomainResult<GroupInviteInfo>

    /**
     * Actualiza la configuración de entrada al grupo.
     */
    suspend fun updateInviteSettings(groupId: String, requiresApproval: Boolean): DomainResult<Unit>

    /**
     * Observa las matrículas de un grupo del profesor: solicitudes pendientes e inscritos.
     * Se vuelve a emitir después de [decideEnrollment].
     */
    fun observeGroupStudents(groupId: String): Flow<List<GroupStudent>>

    /** El profesor aprueba ([approve] = true) o rechaza una solicitud pendiente. */
    suspend fun decideEnrollment(enrollmentId: String, approve: Boolean): DomainResult<Unit>

    /**
     * Crea un curso con su primer grupo y devuelve el código de invitación del grupo. Si el profesor ya tiene un curso
     * con ese código no es un error: el grupo se agrega a ese curso. Si el código es de otro profesor, falla.
     */
    suspend fun createCourse(newCourse: NewCourse): DomainResult<GroupInviteInfo>

    /** Agrega un grupo a un curso del profesor y devuelve el código de invitación del grupo nuevo. */
    suspend fun addGroup(courseId: String, newGroup: NewGroup): DomainResult<GroupInviteInfo>

    /** Elimina un curso del profesor con sus grupos. Falla si todavía tiene estudiantes matriculados. */
    suspend fun deleteCourse(courseId: String): DomainResult<Unit>

    /**
     * El estudiante sale de su grupo (o cancela una solicitud pendiente). La matrícula no se borra: así conserva sus
     * apuntes y los recupera si vuelve a unirse. Deja de aparecer en [observeMyEnrollments].
     */
    suspend fun leaveEnrollment(enrollmentId: String): DomainResult<Unit>

    /** El profesor del grupo quita a un estudiante inscrito. Conserva sus apuntes; si quiere volver, queda pendiente. */
    suspend fun removeStudent(groupId: String, enrollmentId: String): DomainResult<Unit>
}
