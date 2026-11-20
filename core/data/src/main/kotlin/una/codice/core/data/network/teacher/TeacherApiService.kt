package una.codice.core.data.network.teacher

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import una.codice.core.data.network.isoInstantToEpochMillis
import una.codice.core.data.network.relativeTimeFrom
import una.codice.core.data.network.shortDate
import una.codice.core.domain.model.EnrollmentStatus
import una.codice.core.domain.model.GroupCoverage
import una.codice.core.domain.model.GroupInviteInfo
import una.codice.core.domain.model.GroupModuleCoverage
import una.codice.core.domain.model.GroupStudent
import una.codice.core.domain.model.GroupSubtopicCoverage
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.User
import una.codice.core.domain.model.UserRole
import retrofit2.http.DELETE

/**
 * Lo que el profesor gestiona contra el backend de Códice: sus cursos y grupos, las matrículas de
 * cada grupo y la cobertura agregada. Quién puede hacer qué lo decide el backend con el token: a un
 * estudiante estas rutas le responden 403, y `GET /courses` le devuelve al profesor solo sus cursos.
 */
interface TeacherApiService {

    @GET("api/v1/courses")
    suspend fun getMyCourses(): List<CourseDto>

    @POST("api/v1/courses")
    suspend fun createCourse(@Body request: CourseCreateDto): CourseDto

    /** Solo el dueño. 204 al eliminar; 422 si el curso todavía tiene estudiantes matriculados. */
    @DELETE("api/v1/cursos/{id}")
    suspend fun deleteCourse(@Path("id") courseId: Long)

    @GET("api/v1/groups/course/{courseId}")
    suspend fun getCourseGroups(@Path("courseId") courseId: Long): List<TeacherGroupDto>

    @POST("api/v1/groups")
    suspend fun createGroup(@Body request: GroupCreateDto): TeacherGroupDto

    /** Sin [status] trae todas: pendientes, activas y rechazadas. */
    @GET("api/v1/groups/{groupId}/enrollments")
    suspend fun getGroupEnrollments(
        @Path("groupId") groupId: Long,
        @Query("status") status: String? = null,
    ): List<GroupEnrollmentDto>

    /** `ACTIVE` aprueba la solicitud, `REJECTED` la rechaza. La respuesta no se usa: la lista se vuelve a pedir. */
    @PUT("api/v1/enrollments/{id}/status")
    suspend fun decideEnrollment(@Path("id") enrollmentId: Long, @Body request: EnrollmentStatusDto)

    /** Quitar a un estudiante inscrito. El backend deja la matrícula en REMOVED y conserva sus apuntes. */
    @DELETE("api/v1/groups/{groupId}/enrollments/{enrollmentId}")
    suspend fun removeStudent(@Path("groupId") groupId: Long, @Path("enrollmentId") enrollmentId: Long)

    @POST("api/v1/groups/{id}/invite-code")
    suspend fun regenerateInviteCode(@Path("id") groupId: Long): TeacherGroupDto

    @PATCH("api/v1/groups/{id}")
    suspend fun updateGroupSettings(@Path("id") groupId: Long, @Body request: GroupSettingsDto): TeacherGroupDto

    @GET("api/v1/grupos/{grupoId}/cobertura")
    suspend fun getGroupCoverage(@Path("grupoId") groupId: Long): GroupCoverageDto

    /** [IA-02] Extracción del temario desde un archivo PDF subido por el profesor. */
    @Multipart
    @POST("api/v1/cursos/{cursoId}/temario/extraer")
    suspend fun extractSyllabusFromPdf(
        @Path("cursoId") courseId: Long,
        @Part file: MultipartBody.Part,
    ): ExtractedSyllabusDto
}

/** Árbol propuesto por la IA al extraer un temario desde un PDF ([IA-02]). */
@Serializable
data class ExtractedSyllabusDto(
    val courseId: Long? = null,
    val modules: List<ExtractedModuleDto> = emptyList(),
)

@Serializable
data class ExtractedModuleDto(
    val number: Int,
    val title: String,
    val confidence: Double = 1.0,
    val subtopics: List<ExtractedSubtopicDto> = emptyList(),
)

@Serializable
data class ExtractedSubtopicDto(
    val code: String,
    val title: String,
    val confidence: Double = 1.0,
    val objectives: List<String> = emptyList(),
)

@Serializable
data class CourseDto(
    val id: Long,
    val code: String,
    val name: String,
)

@Serializable
data class CourseCreateDto(val code: String, val name: String)

/** `StudentGroupDTO` visto por su profesor: además trae el código y su vencimiento. */
@Serializable
data class TeacherGroupDto(
    val id: Long,
    val courseId: Long,
    val courseCode: String? = null,
    val courseName: String? = null,
    val teacherName: String? = null,
    val number: String,
    val period: String,
    val inviteCode: String = "",
    val requiresApproval: Boolean = true,
    val inviteExpiresAt: String? = null,
)

@Serializable
data class GroupCreateDto(
    val courseId: Long,
    val number: String,
    val period: String,
    val requiresApproval: Boolean,
)

/** `EnrollmentRequestSummaryDTO`: solo nombre y correo del estudiante, nunca su cobertura. */
@Serializable
data class GroupEnrollmentDto(
    val id: Long,
    val studentName: String = "",
    val studentEmail: String = "",
    val status: String,
    val requestedAt: String = "",
)

@Serializable
data class EnrollmentStatusDto(val status: String)

@Serializable
data class GroupSettingsDto(val requiresApproval: Boolean)

/** Cobertura agregada y anónima del grupo: porcentajes por subtema, sin nada que identifique a nadie. */
@Serializable
data class GroupCoverageDto(
    val groupId: Long,
    val totalStudents: Int = 0,
    val subtopics: List<AggregatedSubtopicDto> = emptyList(),
)

@Serializable
data class AggregatedSubtopicDto(
    val subtopicId: Long,
    val subtopicCode: String,
    val subtopicTitle: String,
    val coveredPercentage: Double = 0.0,
    val partialPercentage: Double = 0.0,
    val noNotesPercentage: Double = 0.0,
    val moduleNumber: Int? = null,
    val moduleTitle: String? = null,
)

fun TeacherGroupDto.toStudentGroup(): StudentGroup = StudentGroup(
    id = id.toString(),
    courseId = courseId.toString(),
    courseName = courseName.orEmpty(),
    groupNumber = number,
    period = period,
    professorName = teacherName.orEmpty(),
    courseCode = courseCode.orEmpty(),
)

fun TeacherGroupDto.toInviteInfo(): GroupInviteInfo = GroupInviteInfo(
    groupId = id.toString(),
    inviteCode = inviteCode,
    expiresAt = inviteExpiresAt?.let { shortDate(isoInstantToEpochMillis(it)) }
        ?.takeIf { it.isNotEmpty() }
        ?.let { "Vence el $it" },
    requiresApproval = requiresApproval,
)

/** El backend manda el nombre completo; se parte en el primer espacio para el modelo [User]. */
fun GroupEnrollmentDto.toGroupStudent(now: Long = System.currentTimeMillis()): GroupStudent {
    val name = studentName.trim()
    val firstName = name.substringBefore(' ')
    val lastName = name.substringAfter(' ', missingDelimiterValue = "")
    return GroupStudent(
        user = User(id = "", firstName = firstName, lastName = lastName, email = studentEmail, role = UserRole.STUDENT),
        enrollmentId = id.toString(),
        status = EnrollmentStatus.entries.firstOrNull { it.name == status } ?: EnrollmentStatus.PENDING,
        requestDate = relativeTimeFrom(isoInstantToEpochMillis(requestedAt), now),
    )
}

/** Agrupa los subtemas por módulo en el orden del temario, igual que el mapa del estudiante. */
fun GroupCoverageDto.toDomain(): GroupCoverage = GroupCoverage(
    totalStudents = totalStudents,
    modules = subtopics
        .groupBy { (it.moduleNumber ?: 0) to it.moduleTitle.orEmpty() }
        .map { (module, rows) ->
            GroupModuleCoverage(
                moduleNumber = module.first,
                moduleTitle = module.second,
                subtopics = rows.map {
                    GroupSubtopicCoverage(
                        subtopicId = it.subtopicId.toString(),
                        subtopicCode = it.subtopicCode,
                        subtopicTitle = it.subtopicTitle,
                        coveredPercentage = it.coveredPercentage,
                        partialPercentage = it.partialPercentage,
                        noNotesPercentage = it.noNotesPercentage,
                    )
                },
            )
        }
        .sortedBy { it.moduleNumber },
)
