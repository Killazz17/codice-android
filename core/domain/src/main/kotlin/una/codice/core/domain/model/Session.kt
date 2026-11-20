package una.codice.core.domain.model

data class Session(
    /** Credencial que acompaña cada llamada al servidor para probar quién eres. */
    val token: String,

    /** `uuid` del usuario dueño de esta sesión. */
    val userId: String,

    /** Decide si la app arranca en el grafo del estudiante o en el del profesor. */
    val role: UserRole,

    /** Nombre del usuario logueado para mostrar en la interfaz (ej: "Sebastian"). */
    val userName: String = "Sebastian",

    /** Correo con el que el usuario inició sesión. Vacío en las sesiones guardadas antes de que se conservara. */
    val email: String = "",
)
