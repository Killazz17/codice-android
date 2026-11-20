package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ValidateEmailUseCase].
 *
 * Nota sobre el estilo: los nombres de los tests van entre backticks (`` ` ``)
 * y con espacios. Kotlin lo permite en tests y hace que el informe de fallos se
 * lea como una frase en español en vez de como `testEmailInvalido_2`.
 *
 * No llevan tildes a propósito: van a parar a nombres de archivo `.class`
 * generados, y en Windows los caracteres especiales en rutas largas dan
 * problemas (nos pasó y rompió un commit entero).
 */
class ValidateEmailUseCaseTest {

    // No necesita ningún doble de prueba: esta clase no depende de nada.
    // Esa es la ventaja de tener la validación aislada en su propio caso de uso.
    private val validateEmail = ValidateEmailUseCase()

    @Test
    fun `un correo bien formado se acepta`() {
        val resultado = validateEmail("ana.rojas@est.una.ac.cr")

        assertEquals(DomainResult.Success("ana.rojas@est.una.ac.cr"), resultado)
    }

    /**
     * Protege la normalización. Sin ella, alguien que se registra como
     * "Ana@UNA.ac.cr" no podría entrar escribiendo "ana@una.ac.cr", y el
     * usuario no tendría forma de entender por qué.
     */
    @Test
    fun `el correo se normaliza a minusculas y sin espacios`() {
        val resultado = validateEmail("  Ana@UNA.ac.CR  ")

        assertEquals(DomainResult.Success("ana@una.ac.cr"), resultado)
    }

    /** El campo vacío debe dar un mensaje propio, no el genérico de formato. */
    @Test
    fun `un correo vacio devuelve error de campo obligatorio`() {
        val error = validateEmail("   ") as DomainResult.Failure

        val validacion = error.error as DomainError.Validation
        assertEquals(DomainError.Field.EMAIL, validacion.field)
        assertEquals("El correo es obligatorio", validacion.message)
    }

    /**
     * Recorre de una vez los errores de tipeo reales que comete la gente.
     * Si alguien "simplifica" el Regex y deja pasar alguno, este test lo caza.
     */
    @Test
    fun `los formatos invalidos se rechazan`() {
        val invalidos = listOf(
            "ana",                 // sin arroba
            "ana@",                // sin dominio
            "ana@una",             // sin extension
            "@una.ac.cr",          // sin nombre
            "ana ro@una.ac.cr",    // espacio interno
            "ana@@una.ac.cr",      // doble arroba
        )

        invalidos.forEach { entrada ->
            val resultado = validateEmail(entrada)
            assertTrue("Se esperaba rechazar: $entrada", resultado is DomainResult.Failure)
            val validacion = (resultado as DomainResult.Failure).error as DomainError.Validation
            // Siempre debe apuntar al campo del correo, para que la UI sepa
            // cuál marcar en rojo.
            assertEquals(DomainError.Field.EMAIL, validacion.field)
        }
    }

    /**
     * Criterio de aceptación del issue #2: "cada regla devuelve un mensaje en
     * español, listo para mostrar". La UI no debe traducir códigos de error.
     */
    @Test
    fun `el mensaje de error viene en espanol`() {
        val error = validateEmail("ana") as DomainResult.Failure

        assertTrue(error.error.message.startsWith("Escribe un correo válido"))
    }
}
