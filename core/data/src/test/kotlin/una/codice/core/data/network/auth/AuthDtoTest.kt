package una.codice.core.data.network.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import una.codice.core.domain.model.UserRole

class AuthDtoTest {

    @Test
    fun `la sesion lleva el nombre completo del usuario y su rol, no un nombre de relleno`() {
        val usuario = UserDto(id = 7, firstName = "Ana", lastName = "Rojas", email = "ana@est.una.ac.cr", roles = listOf("ROLE_STUDENT"))

        val sesion = usuario.toSession("token-1")

        assertEquals("Ana Rojas", sesion.userName)
        assertEquals("ana@est.una.ac.cr", sesion.email)
        assertEquals("7", sesion.userId)
        assertEquals(UserRole.STUDENT, sesion.role)
        assertEquals("token-1", sesion.token)
    }

    @Test
    fun `sin nombre en la respuesta se usa la parte local del correo`() {
        val usuario = UserDto(id = 8, email = "profe.luis@una.ac.cr", roles = listOf("ROLE_TEACHER"))

        val sesion = usuario.toSession("token-2")

        assertEquals("profe.luis", sesion.userName)
        assertEquals(UserRole.TEACHER, sesion.role)
    }
}
