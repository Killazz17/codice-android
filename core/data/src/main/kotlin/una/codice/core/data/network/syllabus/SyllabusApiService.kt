package una.codice.core.data.network.syllabus

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * El temario del curso contra el backend de Códice (API-05): leerlo, escribir sus módulos, subtemas y objetivos, y
 * publicarlo. La extracción desde el PDF (`POST /cursos/{id}/temario/extraer`) vive en `TeacherApiService`.
 *
 * Todo es del profesor dueño del curso (403 en otro caso). El backend no tiene todavía cómo renombrar ni quitar un
 * módulo ya creado: solo subtemas y objetivos.
 */
interface SyllabusApiService {

    /** El árbol módulo → subtemas → objetivos. El profesor dueño lo ve siempre, esté o no publicado. */
    @GET("api/v1/cursos/{cursoId}/temario")
    suspend fun getSyllabus(@Path("cursoId") courseId: Long): SyllabusDto

    /** Responde 409 si el curso ya tiene un módulo con ese número. */
    @POST("api/v1/cursos/{cursoId}/modulos")
    suspend fun createModule(@Path("cursoId") courseId: Long, @Body request: ModuleCreateRequestDto): ModuleDto

    /** Actualización parcial: un campo en `null` no se toca. Responde 409 si el número ya lo tiene otro módulo. */
    @PATCH("api/v1/modulos/{id}")
    suspend fun updateModule(@Path("id") moduleId: Long, @Body request: ModuleUpdateRequestDto): ModuleDto

    /** Borra el módulo con sus subtemas y objetivos. Responde 422 si algún subtema ya tiene apuntes de estudiantes. */
    @DELETE("api/v1/modulos/{id}")
    suspend fun deleteModule(@Path("id") moduleId: Long)

    /**
     * Los ids de TODOS los módulos del curso en el orden nuevo: el servidor los renumera 1 a n en una transacción
     * (dos `PATCH` seguidos chocarían con la unicidad del número). Responde 422 si falta, sobra o se repite alguno.
     */
    @PUT("api/v1/cursos/{cursoId}/modulos/orden")
    suspend fun reorderModules(@Path("cursoId") courseId: Long, @Body request: ModuleOrderRequestDto): List<ModuleDto>

    /** Responde 409 si el módulo ya tiene un subtema con ese código. */
    @POST("api/v1/modulos/{moduloId}/subtemas")
    suspend fun createSubtopic(@Path("moduloId") moduleId: Long, @Body request: SubtopicCreateRequestDto): SubtopicDto

    /** Actualización parcial: un campo en `null` no se toca. */
    @PATCH("api/v1/subtemas/{id}")
    suspend fun updateSubtopic(@Path("id") subtopicId: Long, @Body request: SubtopicUpdateRequestDto): SubtopicDto

    /** Responde 422 si el subtema ya tiene apuntes de estudiantes. */
    @DELETE("api/v1/subtemas/{id}")
    suspend fun deleteSubtopic(@Path("id") subtopicId: Long)

    @POST("api/v1/subtemas/{id}/objetivos")
    suspend fun addObjective(@Path("id") subtopicId: Long, @Body request: ObjectiveRequestDto): ObjectiveDto

    @DELETE("api/v1/objetivos/{id}")
    suspend fun deleteObjective(@Path("id") objectiveId: Long)

    /** Marca el temario como publicado. Responde 422 si no tiene ningún subtema. */
    @POST("api/v1/cursos/{cursoId}/temario/publicar")
    suspend fun publish(@Path("cursoId") courseId: Long): PublishDto
}
