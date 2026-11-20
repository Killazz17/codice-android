package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ValidatePasswordUseCase]: una prueba por cada regla del issue #2.
 *
 * Están separados a propósito en vez de agrupados en un solo test con varias
 * comprobaciones: si falla, el informe dice exactamente cuál regla se rompió.
 */
class ValidatePasswordUseCaseTest {

    private val validatePassword = ValidatePasswordUseCase()

    @Test
    fun `una contrasenia valida se acepta`() {
        // "codice2026" cumple las tres: 10 caracteres, tiene letras y números.
        assertTrue(validatePassword("codice2026") is DomainResult.Success)
    }

    /** Regla: mínimo 8 caracteres. Se comprueba también el texto del mensaje. */
    @Test
    fun `menos de ocho caracteres se rechaza`() {
        val fallo = validatePassword("cod123") as DomainResult.Failure

        val validacion = fallo.error as DomainError.Validation
        assertEquals(DomainError.Field.PASSWORD, validacion.field)
        assertEquals("La contraseña debe tener al menos 8 caracteres", validacion.message)
    }

    /** Regla: al menos una letra. Ocho dígitos no bastan. */
    @Test
    fun `sin letra se rechaza`() {
        val fallo = validatePassword("12345678") as DomainResult.Failure

        assertEquals(
            "La contraseña debe incluir al menos una letra",
            (fallo.error as DomainError.Validation).message,
        )
    }

    /** Regla: al menos un número. Doce letras tampoco bastan. */
    @Test
    fun `sin numero se rechaza`() {
        val fallo = validatePassword("codicecodice") as DomainResult.Failure

        assertEquals(
            "La contraseña debe incluir al menos un número",
            (fallo.error as DomainError.Validation).message,
        )
    }

    /**
     * Lo que de verdad protege este test no es que detecte la diferencia
     * —eso es obvio— sino que el error apunte a PASSWORD_CONFIRMATION.
     * Si apuntara a PASSWORD, la UI marcaría en rojo la caja de arriba y el
     * usuario borraría la contraseña buena en vez de corregir la repetición.
     */
    @Test
    fun `confirmacion no coincide`() {
        val fallo = validatePassword("codice2026", "codice2027") as DomainResult.Failure

        val validacion = fallo.error as DomainError.Validation
        assertEquals(DomainError.Field.PASSWORD_CONFIRMATION, validacion.field)
        assertEquals("Las contraseñas no coinciden", validacion.message)
    }

    /**
     * Fija la decisión de comparar sin recortar espacios. Si alguien "arregla"
     * esto añadiendo un `.trim()`, el usuario acabaría con una contraseña que
     * no es la que cree haber escrito.
     */
    @Test
    fun `espacio final en confirmacion cuenta como diferencia`() {
        val fallo = validatePassword("codice2026", "codice2026 ") as DomainResult.Failure

        assertEquals(
            DomainError.Field.PASSWORD_CONFIRMATION,
            (fallo.error as DomainError.Validation).field,
        )
    }

    /**
     * Comprueba que el parámetro opcional funciona: con `null` solo se evalúan
     * las reglas de fortaleza. Es lo que permite reutilizar este caso de uso
     * fuera del registro sin duplicar las reglas.
     */
    @Test
    fun `sin confirmacion solo se evaluan las reglas de fortaleza`() {
        assertTrue(validatePassword("codice2026", null) is DomainResult.Success)
    }
}
