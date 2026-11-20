package una.codice.core.domain.model

/**
 * Los datos de un registro que **ya pasaron todas las validaciones**, listos
 * para entregárselos al repositorio.
 *
 * ### Para qué sirve una clase que solo agrupa campos
 *
 * Dos motivos, y el segundo es el importante:
 *
 * 1. **Legibilidad.** Sin ella,
 *    [una.codice.core.domain.repository.AuthRepository.register] recibiría seis
 *    parámetros `String` sueltos, y sería facilísimo intercambiar el nombre con
 *    el apellido sin que el compilador se queje.
 *
 * 2. **El tipo comunica una garantía.** Este objeto solo lo construye
 *    [una.codice.core.domain.usecase.auth.RegisterUseCase], y solo después de
 *    validar todo. Entonces, cuando el repositorio recibe un `NewUser`, ya sabe
 *    que el correo tiene formato válido, que la contraseña cumple las reglas y
 *    que el rol fue elegido. **No necesita volver a validar nada.**
 *    Es un patrón conocido como "parse, don't validate": en vez de arrastrar
 *    datos dudosos y comprobarlos en cada capa, se convierten una sola vez a un
 *    tipo que, por existir, ya demuestra que son correctos.
 */
data class NewUser(
    val firstName: String,
    val lastName: String,
    val email: String,

    /**
     * Viaja al repositorio para crear la cuenta, pero **nunca se persiste** en
     * el dispositivo: ver la nota en [Session].
     */
    val password: String,

    val role: UserRole,
)
