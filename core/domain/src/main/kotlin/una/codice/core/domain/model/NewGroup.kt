package una.codice.core.domain.model

/**
 * Lo que el profesor llena para agregar un grupo a un curso suyo que ya existe. Es lo mismo que [NewCourse] sin el
 * código y el nombre: el curso ya está elegido.
 *
 * @property groupNumber Número del grupo, p. ej. `03`.
 * @property period Periodo lectivo, p. ej. `2026-II`.
 * @property requiresApproval Si unirse con el código pide la aprobación del profesor.
 */
data class NewGroup(
    val groupNumber: String,
    val period: String,
    val requiresApproval: Boolean,
)
