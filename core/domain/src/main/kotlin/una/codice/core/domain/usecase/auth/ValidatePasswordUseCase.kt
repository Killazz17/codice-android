package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.result.validationFailure

/**
 * Valida la fortaleza de una contraseña y, si se le pasa, su confirmación.
 *
 * Las reglas salen tal cual del issue #2:
 * mínimo 8 caracteres, al menos una letra y al menos un número.
 *
 * Igual que [ValidateEmailUseCase], es un caso de uso independiente para que el
 * ViewModel pueda validar mientras el usuario escribe.
 */
class ValidatePasswordUseCase {

    /**
     * @param password la contraseña escrita.
     * @param confirmation la repetición.
     *
     * ### Por qué `confirmation` es opcional (`null` por defecto)
     *
     * Para poder reutilizar el mismo caso de uso en dos sitios sin duplicar las
     * reglas de fortaleza:
     *
     * - **Registro**: se pasan las dos y además se comprueba que coincidan.
     * - **Cambio de contraseña o validación al vuelo**: se pasa solo la
     *   primera.
     *
     * Si en cambio hubiéramos hecho dos casos de uso separados, las reglas de
     * los 8 caracteres, la letra y el número estarían escritas dos veces, y
     * tarde o temprano alguien cambiaría una y olvidaría la otra.
     */
    operator fun invoke(password: String, confirmation: String? = null): DomainResult<Unit> {
        if (password.isEmpty()) {
            return validationFailure(DomainError.Field.PASSWORD, "La contraseña es obligatoria")
        }
        if (password.length < MIN_LENGTH) {
            return validationFailure(
                DomainError.Field.PASSWORD,
                "La contraseña debe tener al menos $MIN_LENGTH caracteres",
            )
        }
        // `none { }` devuelve true si NINGÚN carácter cumple la condición.
        // Se lee mejor que `!password.any { it.isLetter() }`.
        if (password.none { it.isLetter() }) {
            return validationFailure(
                DomainError.Field.PASSWORD,
                "La contraseña debe incluir al menos una letra",
            )
        }
        if (password.none { it.isDigit() }) {
            return validationFailure(
                DomainError.Field.PASSWORD,
                "La contraseña debe incluir al menos un número",
            )
        }
        // La confirmación se compara con `!=` exacto, SIN recortar espacios.
        // Un espacio invisible al final es una diferencia real, y es mucho
        // mejor que el usuario la descubra ahora —cuando puede corregirla— y no
        // mañana, cuando intente iniciar sesión y no entienda por qué falla.
        //
        // Nota: el error apunta a PASSWORD_CONFIRMATION y no a PASSWORD, para
        // que la UI marque en rojo la caja de abajo (la que hay que corregir) y
        // no la de arriba.
        if (confirmation != null && password != confirmation) {
            return validationFailure(
                DomainError.Field.PASSWORD_CONFIRMATION,
                "Las contraseñas no coinciden",
            )
        }
        // `Unit` es el equivalente de `void`: esta función no tiene nada que
        // devolver, solo confirma que no hubo errores.
        return DomainResult.Success(Unit)
    }

    companion object {
        /**
         * Público a propósito: la pantalla de registro muestra "Mínimo N caracteres" con este mismo número.
         *
         * Está en una constante y no escrito a mano en cada sitio para que el
         * número del mensaje de error y el de la comprobación no puedan
         * desincronizarse nunca.
         */
        const val MIN_LENGTH = 8
    }
}
