package una.codice.core.data.network.review

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Los repasos con repetición espaciada contra el backend de Códice (IA-05). La IA que escribe las preguntas corre en el
 * servidor: la app solo las pide y manda el resultado, y nunca lleva una llave de ningún proveedor.
 */
interface ReviewApiService {

    /** Los repasos que ya vencieron (hoy o antes), con su pregunta y el origen del vacío. Un profesor recibe 403. */
    @GET("api/v1/repasos/pendientes")
    suspend fun getPending(): List<PendingReviewDto>

    /**
     * Los repasos ya generados que todavía no vencen, del más próximo al más lejano, con su pregunta. Sirven para
     * practicarlos antes de tiempo sin cambiar el calendario. Un profesor recibe 403.
     */
    @GET("api/v1/repasos/programados")
    suspend fun getScheduled(): List<PendingReviewDto>

    /**
     * La IA crea una pregunta por cada subtema con vacío que todavía no tenga repaso (hasta 5 por llamada). Responde
     * 201 con los repasos nuevos, que empiezan a tocar al día siguiente, y 503 si la IA no responde.
     */
    @POST("api/v1/repasos/generar")
    suspend fun generate(): List<PendingReviewDto>

    /**
     * Registra `PASSED` o `FAILED`: aprobar sube al siguiente intervalo (1, 3, 7, 14, 30 días) y fallar vuelve a 1.
     * Responde 422 si el repaso todavía no vence y 403 si no es del estudiante.
     */
    @POST("api/v1/repasos/{id}/resultado")
    suspend fun recordResult(@Path("id") reviewId: Long, @Body request: ReviewResultRequestDto): ReviewScheduleDto
}
