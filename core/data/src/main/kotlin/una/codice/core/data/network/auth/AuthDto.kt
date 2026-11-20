package una.codice.core.data.network.auth

import kotlinx.serialization.Serializable
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponseDto(
    val token: String? = null,
    val accessToken: String? = null,
    val jwt: String? = null,
)

@Serializable
data class SignUpRequestDto(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val roleName: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val enabled: Boolean = true,
    val roles: List<String> = emptyList(),
)

private const val ROLE_PREFIX = "ROLE_"

fun NewUser.toSignUpDto(): SignUpRequestDto = SignUpRequestDto(
    firstName = firstName,
    lastName = lastName,
    email = email,
    password = password,
    roleName = ROLE_PREFIX + role.name,
)

fun UserDto.toSession(token: String): Session = Session(
    token = token,
    userId = id.toString(),
    role = if (ROLE_PREFIX + UserRole.TEACHER.name in roles) UserRole.TEACHER else UserRole.STUDENT,
    // El nombre real del usuario; si el backend no lo trae, la parte local del correo. Nunca el de otra persona.
    userName = "$firstName $lastName".trim().ifBlank { email.substringBefore('@') },
    email = email,
)

/** Cuerpo de `POST /api/v1/auth/change-password`: la contraseña actual (para confirmar quién es) y la nueva. */
@Serializable
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String,
)
