package una.codice.core.data.network.enrollment

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import una.codice.core.domain.model.Enrollment
import una.codice.core.domain.model.EnrollmentStatus
import una.codice.core.domain.model.StudentGroup
import retrofit2.http.DELETE

/**
 * Matrículas del estudiante contra el backend de Códice.
 *
 * Unirse a un curso son dos llamadas: buscar el grupo por su código de invitación y solicitar la
 * matrícula con el id del grupo. Ninguna recibe el id del estudiante: el backend usa el dueño del
 * token, así nadie puede matricular ni ver las matrículas de otra persona.
 */
interface EnrollmentApiService {

    @GET("api/v1/groups/invite/{code}")
    suspend fun findGroupByInviteCode(@Path("code") code: String): StudentGroupDto

    /** Queda `PENDING` si el grupo pide aprobación del profesor, o `ACTIVE` si no. */
    @POST("api/v1/enrollments")
    suspend fun requestEnrollment(@Body request: EnrollmentRequestDto): EnrollmentDto

    @GET("api/v1/enrollments/my")
    suspend fun getMyEnrollments(): List<EnrollmentDto>

    /** Salir del grupo, o cancelar una solicitud pendiente. El backend deja la matrícula en LEFT y conserva los apuntes. */
    @DELETE("api/v1/enrollments/{id}")
    suspend fun leaveEnrollment(@Path("id") enrollmentId: Long)
}

@Serializable
data class EnrollmentRequestDto(val groupId: Long)

/** `StudentGroupDTO` del backend; solo los campos que muestra la app. */
@Serializable
data class StudentGroupDto(
    val id: Long,
    val courseId: Long,
    val courseCode: String? = null,
    val courseName: String? = null,
    val teacherName: String? = null,
    val number: String,
    val period: String,
)

/** `EnrollmentDTO` del backend. `status`: `PENDING`, `ACTIVE` o `REJECTED`. */
@Serializable
data class EnrollmentDto(
    val id: Long,
    val groupId: Long,
    val groupNumber: String? = null,
    val courseId: Long,
    val courseCode: String? = null,
    val courseName: String? = null,
    val status: String,
) {
    val isActive: Boolean get() = status == "ACTIVE"
}

fun StudentGroupDto.toDomain(): StudentGroup = StudentGroup(
    id = id.toString(),
    courseId = courseId.toString(),
    courseName = courseName.orEmpty(),
    groupNumber = number,
    period = period,
    professorName = teacherName.orEmpty(),
    courseCode = courseCode.orEmpty(),
)

/** La matrícula no trae el periodo ni el profesor; la lista de "Mis cursos" no los muestra. */
fun EnrollmentDto.toDomain(): Enrollment = Enrollment(
    id = id.toString(),
    group = StudentGroup(
        id = groupId.toString(),
        courseId = courseId.toString(),
        courseName = courseName.orEmpty(),
        groupNumber = groupNumber.orEmpty(),
        period = "",
        professorName = "",
        courseCode = courseCode.orEmpty(),
    ),
    // Un estado que la app no conoce se muestra como pendiente: no habilita entrar al curso.
    status = EnrollmentStatus.entries.firstOrNull { it.name == status } ?: EnrollmentStatus.PENDING,
)
