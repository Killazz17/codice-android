package una.codice.core.domain.error

/**
 * Todo lo que puede salir mal en Códice, expresado en lenguaje de negocio y no
 * de red ni de base de datos.
 *
 * Aquí no hay `SocketTimeoutException` ni `SQLiteConstraintException`. Esas son
 * cosas de la capa de datos; `:core:data` las traduce a uno de estos casos
 * antes de dejarlas subir. La UI solo tiene que saber pintar estos.
 *
 * ### Qué es un `sealed interface` y por qué importa aquí
 *
 * "Sealed" (sellado) significa que **la lista de implementaciones está cerrada**
 * y toda ella vive en este archivo. Nadie puede crear un `DomainError` nuevo
 * desde fuera.
 *
 * El beneficio es concreto. Cuando el ViewModel haga:
 *
 * ```kotlin
 * when (error) {
 *     is DomainError.Validation        -> marcarCampoEnRojo(error.field)
 *     is DomainError.InvalidCredentials -> mostrarMensaje(error.message)
 *     is DomainError.Network            -> mostrarBotonReintentar()
 *     is DomainError.Unauthorized       -> volverAlLogin()
 *     is DomainError.Unknown            -> mostrarMensaje(error.message)
 * }
 * ```
 *
 * ...el compilador verifica que estén **todos** los casos. Y si mañana alguien
 * agrega un caso nuevo aquí, el proyecto deja de compilar y señala cada `when`
 * que hay que actualizar. Con excepciones normales eso no pasa: el caso nuevo
 * se te escapa en silencio y estalla en tiempo de ejecución, delante del
 * usuario.
 *
 * ### Por qué no heredan de `Exception`
 *
 * Un correo mal escrito **no es una excepción**: es un resultado esperado y
 * frecuentísimo del flujo normal. Lanzar y atrapar excepciones para eso es caro
 * y hace que el camino de error sea invisible en las firmas de las funciones.
 * Ver [una.codice.core.domain.result.DomainResult].
 */
sealed interface DomainError {

    /**
     * Mensaje ya redactado en español y listo para pintarse tal cual.
     *
     * La capa de UI no debe traducir códigos de error ni construir frases: eso
     * dispersa el texto por toda la app. Aquí está el mensaje final.
     */
    val message: String

    /**
     * Un campo del formulario incumple una regla.
     *
     * @property field Qué caja de texto marcar en rojo. Es un [Field] (enum) y
     *   no un `String` justamente para que la UI pueda decidir con un `when`
     *   exhaustivo, sin comparar cadenas mágicas como `"email"` que se rompen
     *   con un typo y que el compilador no puede revisar.
     */
    data class Validation(
        val field: Field,
        override val message: String,
    ) : DomainError

    /** El correo o la contraseña no coinciden con ninguna cuenta registrada. */
    data class InvalidCredentials(
        override val message: String = "Correo o contraseña incorrectos",
    ) : DomainError

    /** No se pudo hablar con el servidor: sin internet, caído o timeout. */
    data class Network(
        override val message: String = "No se pudo conectar. Revisa tu conexión e intenta de nuevo",
    ) : DomainError

    /** Había sesión, pero el token expiró o fue revocado. Hay que volver al login. */
    data class Unauthorized(
        override val message: String = "Tu sesión expiró. Inicia sesión de nuevo",
    ) : DomainError

    /**
     * Red de seguridad: algo falló y no encaja en ninguna categoría conocida.
     *
     * Existe para que la capa de datos nunca tenga la excusa de dejar escapar
     * una excepción cruda hacia arriba.
     */
    data class Unknown(
        override val message: String = "Ocurrió un error inesperado",
    ) : DomainError

    /**
     * Los campos del formulario de acceso que pueden fallar una validación.
     *
     * La UI usa esto para saber exactamente qué caja resaltar. El orden de
     * arriba a abajo es el mismo del formulario, que es también el orden en que
     * [una.codice.core.domain.usecase.auth.RegisterUseCase] evalúa las reglas.
     */
    enum class Field {
        FIRST_NAME,
        LAST_NAME,
        EMAIL,
        PASSWORD,
        PASSWORD_CONFIRMATION,
        ROLE,
        CODE,
    }
}
