package una.codice.core.domain.result

import una.codice.core.domain.error.DomainError

/**
 * El resultado de cualquier operación de dominio: **o trae un dato, o trae un
 * [DomainError]**. Nunca las dos cosas, nunca ninguna.
 *
 * Toda función que pueda fallar devuelve esto. Así el fallo queda escrito en la
 * firma y quien llama está obligado por el compilador a decidir qué hace con
 * él; no hay forma de ignorarlo por accidente como pasa con las excepciones.
 *
 * ### Por qué no usamos el `Result<T>` que ya trae Kotlin
 *
 * Dos razones concretas:
 *
 * 1. **`Result<T>` obliga a que el fallo sea un `Throwable`.** En Códice, un
 *    correo mal escrito no es una excepción: es un resultado esperado del flujo
 *    normal. Envolverlo en una excepción es caro (construir el stack trace) y
 *    conceptualmente falso.
 *
 * 2. **Con un tipo de error propio y sellado, el `when` es exhaustivo.** El
 *    `Result<T>` de Kotlin te devuelve un `Throwable` genérico y te toca
 *    adivinar con `if (e is X)`. Aquí el compilador te obliga a cubrir todos
 *    los casos de [DomainError]. Ver la explicación completa allí.
 *
 * ### Cómo se usa
 *
 * ```kotlin
 * when (val resultado = loginUseCase(correo, clave)) {
 *     is DomainResult.Success -> irAlGrafoDe(resultado.data.role)
 *     is DomainResult.Failure -> mostrar(resultado.error.message)
 * }
 * ```
 *
 * @param T el tipo del dato que se devuelve cuando todo sale bien.
 */
sealed interface DomainResult<out T> {

    /** La operación salió bien y [data] trae el resultado. */
    data class Success<out T>(val data: T) : DomainResult<T>

    /**
     * La operación falló con [error].
     *
     * ### Por qué es `DomainResult<Nothing>` y no `DomainResult<T>`
     *
     * `Nothing` es el tipo de Kotlin que no tiene ningún valor posible, y es
     * subtipo de absolutamente todos los demás. Combinado con el `out T` de la
     * interfaz (que significa "si A es subtipo de B, entonces
     * `DomainResult<A>` es subtipo de `DomainResult<B>`"), el efecto práctico
     * es que **un `Failure` encaja en cualquier `DomainResult<loQueSea>` sin
     * castings ni genéricos explícitos**.
     *
     * Gracias a eso, en `RegisterUseCase` se puede escribir simplemente
     * `return r` cuando `r` es el `Failure` que devolvió una validación de tipo
     * `DomainResult<String>`, aunque la función devuelva
     * `DomainResult<Session>`. Sin esto habría que reconstruir el error a mano
     * en cada paso.
     */
    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

/**
 * El dato si hubo éxito, o `null` si hubo fallo.
 *
 * Útil cuando solo interesa el camino feliz y el error ya se manejó en otro
 * lado. Si necesitas reaccionar al error, usa un `when` en vez de esto.
 */
fun <T> DomainResult<T>.getOrNull(): T? = (this as? DomainResult.Success)?.data

/** El error si hubo fallo, o `null` si salió bien. */
fun <T> DomainResult<T>.errorOrNull(): DomainError? = (this as? DomainResult.Failure)?.error

/** Azúcar para leer condiciones sin escribir `is DomainResult.Success` a cada rato. */
val DomainResult<*>.isSuccess: Boolean get() = this is DomainResult.Success

/**
 * Atajo para construir un fallo de validación, que es el caso más repetido en
 * todo el módulo.
 *
 * Sin esto, cada regla tendría que escribir el trabalenguas
 * `DomainResult.Failure(DomainError.Validation(campo, mensaje))`. Con más de
 * quince reglas de validación en el registro, el ruido se nota.
 */
fun validationFailure(field: DomainError.Field, message: String): DomainResult<Nothing> =
    DomainResult.Failure(DomainError.Validation(field, message))
