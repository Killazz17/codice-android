package una.codice.core.domain.model

/**
 * Lo que el profesor llena en "Crear curso": el curso y su primer grupo. El código de invitación lo
 * genera el backend.
 *
 * @property code Código del curso, p. ej. `EIF411`.
 * @property groupNumber Número del grupo, p. ej. `01`.
 * @property period Periodo lectivo, p. ej. `2026-II`.
 * @property requiresApproval Si unirse con el código pide la aprobación del profesor.
 */
data class NewCourse(
    val code: String,
    val name: String,
    val groupNumber: String,
    val period: String,
    val requiresApproval: Boolean,
)
