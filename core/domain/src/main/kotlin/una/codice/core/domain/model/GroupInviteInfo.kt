package una.codice.core.domain.model

/**
 * Información de invitación para un grupo.
 *
 * Contiene los datos necesarios para generar el QR y mostrar el código
 * al profesor, incluyendo la configuración de seguridad (aprobación)
 * y la vigencia del código actual.
 */
data class GroupInviteInfo(
    val groupId: String,
    val inviteCode: String,
    val expiresAt: String? = null,
    val requiresApproval: Boolean = true
)
