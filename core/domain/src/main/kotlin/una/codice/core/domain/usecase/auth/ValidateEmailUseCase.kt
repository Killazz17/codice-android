package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.result.validationFailure

/**
 * Valida el formato de un correo y lo devuelve **normalizado**.
 *
 * ### Por qué es un caso de uso propio y no una función privada
 *
 * Porque el ViewModel lo necesita por separado: para poder marcar el campo en
 * rojo **mientras el usuario escribe**, sin llamar al repositorio ni disparar
 * ninguna petición de red. Si la validación viviera escondida dentro de
 * [LoginUseCase], la única forma de saber si un correo es válido sería intentar
 * iniciar sesión.
 *
 * ### Por qué NO usa `android.util.Patterns.EMAIL_ADDRESS`
 *
 * Esa es la forma "normal" de validar correos en Android, y aquí está prohibida
 * a propósito: ataría `:core:domain` al SDK de Android, lo que rompería la
 * regla de la arquitectura y obligaría a usar Robolectric (que arranca un
 * Android falso) solo para probar un formato de texto. Con un [Regex] de Kotlin
 * el test corre en microsegundos.
 */
class ValidateEmailUseCase {

    /**
     * ### Qué es `operator fun invoke`
     *
     * Al llamarse `invoke` y marcarse como `operator`, Kotlin permite usar el
     * objeto **como si fuera una función**:
     *
     * ```kotlin
     * val validateEmail = ValidateEmailUseCase()
     * validateEmail("ana@una.ac.cr")          // en vez de validateEmail.invoke(...)
     * ```
     *
     * Es la convención que sigue todo el proyecto para los casos de uso: se
     * leen como la acción que representan.
     *
     * @return el correo **recortado y en minúsculas** si es válido, o un
     *   `Validation(EMAIL, ...)` con el mensaje ya listo para mostrar.
     */
    operator fun invoke(email: String): DomainResult<String> {
        // Se normaliza ANTES de validar, para que " Ana@UNA.ac.CR " y
        // "ana@una.ac.cr" se traten como la misma cuenta. Sin esto, un usuario
        // que escribe su correo con mayúsculas al registrarse no podría entrar
        // si luego lo escribe en minúsculas.
        val normalized = email.trim().lowercase()

        if (normalized.isEmpty()) {
            return validationFailure(DomainError.Field.EMAIL, "El correo es obligatorio")
        }
        if (!EMAIL_PATTERN.matches(normalized)) {
            return validationFailure(
                DomainError.Field.EMAIL,
                "Escribe un correo válido, por ejemplo nombre@est.una.ac.cr",
            )
        }
        return DomainResult.Success(normalized)
    }

    private companion object {
        /**
         * Patrón deliberadamente simple: `algo@algo.algo`.
         *
         * Es suficiente para atajar los errores reales de tipeo (falta la
         * arroba, falta el dominio, falta la extensión) sin intentar
         * implementar el RFC 5322 completo, que es una fuente conocida de
         * falsos negativos: hay expresiones regulares "correctas" de miles de
         * caracteres que igual rechazan correos válidos. La comprobación de
         * verdad —que el correo exista— solo la puede hacer el servidor.
         *
         * Nota: `Regex.matches()` exige que **toda** la cadena encaje, por eso
         * no hacen falta los anclajes `^` y `$`.
         */
        val EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    }
}
