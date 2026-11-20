package una.codice.core.domain.model

/**
 * Un usuario de Códice, tal como lo entiende la capa de dominio.
 *
 * Es una "entidad de dominio": representa un concepto del negocio, no una fila
 * de la base de datos ni una respuesta JSON del servidor. Esas otras formas
 * (entidad de Room, DTO de Retrofit) viven en `:core:data` y se convierten a
 * esta clase antes de subir. Así, si mañana cambia el JSON del servidor, solo
 * se toca el mapper y no las pantallas.
 *
 * @property id Identificador único. Es `String` y no `Int` porque el servidor
 *   (PostgreSQL) lo emite como `uuid`. El cliente nunca lo genera ni lo
 *   interpreta: solo lo transporta.
 */
data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: UserRole,
) {
    /**
     * Nombre completo listo para mostrar en la barra superior o el perfil.
     *
     * Se calcula aquí, en el dominio, y no en cada pantalla que lo necesite.
     * Si algún día el formato cambia (por ejemplo, "Apellido, Nombre"), se
     * cambia en un solo lugar.
     */
    val fullName: String get() = "$firstName $lastName"
}
