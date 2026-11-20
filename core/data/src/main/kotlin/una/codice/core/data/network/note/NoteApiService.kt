package una.codice.core.data.network.note

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import una.codice.core.data.network.coverage.SubtopicCoverageDto

/**
 * Los apuntes contra el backend de Códice: la lista paginada (`GET /api/v1/apuntes`), la transcripción con IA de la
 * foto de un apunte (`POST /api/v1/apuntes/transcripcion`, IA-03), crear el apunte y auditarlo con IA (IA-04).
 *
 * La IA corre en el servidor: la app solo manda la foto y nunca lleva una llave de ningún proveedor.
 */
interface NoteApiService {

    @GET("api/v1/apuntes")
    suspend fun getNotes(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("grupoId") groupId: Long?,
    ): PageDto<NoteDto>

    /**
     * La foto (JPEG, PNG o WebP, hasta 10 MB) va en el campo multipart `file`. Responde 400 si el archivo no es una foto
     * válida y 503 si la IA no está disponible.
     */
    @Multipart
    @POST("api/v1/apuntes/transcripcion")
    suspend fun transcribePhoto(@Part file: MultipartBody.Part): TranscriptionResultDto

    /**
     * Crea el apunte con el texto que el estudiante confirmó. Es idempotente con `clientRequestId`: reenviar el mismo
     * devuelve el mismo apunte. Responde 403 si la matrícula no es suya o todavía no está aprobada.
     */
    @POST("api/v1/apuntes")
    suspend fun createNote(@Body request: NoteCreateRequestDto): CreatedNoteDto

    /**
     * IA-04: audita el apunte contra los subtemas del curso y devuelve los que tocó, con su estado de cobertura.
     * Responde 422 si el apunte no tiene texto confirmado y 503 si la IA no está disponible.
     */
    @POST("api/v1/apuntes/{id}/auditar")
    suspend fun auditNote(@Path("id") noteId: Long): List<SubtopicCoverageDto>

    /**
     * Borra el apunte. Responde 204 sin cuerpo si salió bien, 403 si el apunte no es del estudiante que llama y 404 si
     * ya no existe.
     */
    @DELETE("api/v1/apuntes/{id}")
    suspend fun deleteNote(@Path("id") noteId: Long)
}
