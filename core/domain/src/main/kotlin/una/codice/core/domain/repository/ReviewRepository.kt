package una.codice.core.domain.repository

import una.codice.core.domain.model.PendingReview
import una.codice.core.domain.model.ReviewOutcome
import una.codice.core.domain.model.ReviewSchedule
import una.codice.core.domain.result.DomainResult

/**
 * Los repasos con repetición espaciada del estudiante ([IA-05]): preguntas de opción múltiple sobre los subtemas
 * donde la auditoría encontró un vacío. El servidor identifica al estudiante por su token y decide los intervalos.
 */
interface ReviewRepository {

    /** Los repasos que ya vencieron (hoy o antes), del más antiguo al más reciente. */
    suspend fun getPendingReviews(): DomainResult<List<PendingReview>>

    /** Los repasos ya generados que todavía no vencen, del más próximo al más lejano: se pueden practicar antes de tiempo. */
    suspend fun getScheduledReviews(): DomainResult<List<PendingReview>>

    /**
     * Pide a la IA una pregunta por cada subtema con vacío que todavía no tenga repaso. Los repasos nuevos empiezan a
     * tocar al día siguiente, así que no aparecen enseguida en [getPendingReviews]. Sin vacíos devuelve una lista vacía.
     */
    suspend fun generateReviews(): DomainResult<List<PendingReview>>

    /** Registra cómo salió el repaso [reviewId] y devuelve cuándo vuelve a tocar. */
    suspend fun recordResult(reviewId: String, outcome: ReviewOutcome): DomainResult<ReviewSchedule>
}
