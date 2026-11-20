package una.codice.core.domain.usecase.auth

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.UserRole
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.result.DomainResult
import una.codice.core.domain.result.validationFailure

/**
 * Crea una cuenta nueva.
 *
 * Aplica **todas** las reglas de validación del issue #2 y solo entonces
 * construye un [NewUser] y se lo entrega al repositorio. Si algo falla, el
 * repositorio ni se entera: no se gasta una llamada de red.
 *
 * ### Por qué devuelve solo el PRIMER fallo
 *
 * Las reglas se evalúan en el mismo orden en que aparecen los campos en el
 * formulario (nombre, apellido, correo, contraseña, confirmación, rol) y se
 * sale con el primer error encontrado.
 *
 * Es una decisión de experiencia de usuario: marcar seis campos en rojo de
 * golpe abruma y no ayuda a saber por dónde empezar. Se corrige uno, se vuelve
 * a intentar, y aparece el siguiente si lo hay.
 *
 * Si en el futuro el equipo prefiere mostrarlos todos a la vez, habría que
 * cambiar el tipo de retorno a algo como `DomainResult<Session, List<Validation>>`
 * y acumular en una lista en lugar de hacer `return` temprano.
 */
class RegisterUseCase(
    private val authRepository: AuthRepository,
    private val validateEmail: ValidateEmailUseCase,
    private val validatePassword: ValidatePasswordUseCase,
) {

    /**
     * @param role **puede ser `null`**, y eso es intencional: el selector de rol
     *   de la pantalla de registro (issue #5) arranca sin nada seleccionado, y
     *   "todavía no eligió" es un estado real que hay que poder representar.
     *
     *   Nótese que el tipo es `UserRole?` y no `String?`. Gracias a eso, el
     *   único caso inválido posible es "no eligió"; **nunca** puede ser "eligió
     *   algo que no existe". Esa mitad de la regla la garantiza el compilador.
     */
    suspend operator fun invoke(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        role: UserRole?,
    ): DomainResult<Session> {
        // --- Orden de validación: el mismo del formulario, de arriba a abajo ---

        val cleanFirstName = when (val r = validateName(firstName, DomainError.Field.FIRST_NAME, "nombre")) {
            is DomainResult.Failure -> return r
            is DomainResult.Success -> r.data
        }

        val cleanLastName = when (val r = validateName(lastName, DomainError.Field.LAST_NAME, "apellido")) {
            is DomainResult.Failure -> return r
            is DomainResult.Success -> r.data
        }

        val normalizedEmail = when (val r = validateEmail(email)) {
            is DomainResult.Failure -> return r
            is DomainResult.Success -> r.data
        }

        // Aquí sí se pasa la confirmación, a diferencia del login: es un
        // registro, y las dos contraseñas tienen que coincidir.
        when (val r = validatePassword(password, passwordConfirmation)) {
            is DomainResult.Failure -> return r
            is DomainResult.Success -> Unit // no hay dato que extraer, solo seguir
        }

        if (role == null) {
            return validationFailure(
                DomainError.Field.ROLE,
                "Elige si ingresas como estudiante o como profesor",
            )
        }

        // A partir de este punto todo está validado. El NewUser es la prueba de
        // ello: el repositorio lo recibe sabiendo que no hace falta revisar nada.
        return authRepository.register(
            NewUser(
                firstName = cleanFirstName,
                lastName = cleanLastName,
                email = normalizedEmail,
                password = password,
                role = role,
            ),
        )
    }

    /**
     * Regla del issue #2 para nombre y apellido: no vacíos y **sin espacios al
     * inicio ni al final**.
     *
     * ### Por qué se rechaza el espacio en vez de recortarlo en silencio
     *
     * Recortarlo sería más cómodo de programar, pero un espacio sobrante casi
     * siempre viene de un copiar-pegar accidental, y a veces esconde un error
     * más grande (un salto de línea, un nombre incompleto). El usuario merece
     * verlo y confirmarlo, porque ese nombre va a quedar así en la lista del
     * grupo que ve el profesor durante todo el semestre.
     *
     * @param label se usa para armar el mensaje ("El nombre es obligatorio" /
     *   "El apellido es obligatorio") sin duplicar la función entera.
     */
    private fun validateName(
        value: String,
        field: DomainError.Field,
        label: String,
    ): DomainResult<String> = when {
        // Los tres casos están separados a propósito para dar un mensaje
        // distinto en cada uno; decirle "es obligatorio" a quien escribió tres
        // espacios sería confuso, porque para él sí escribió algo.
        value.isEmpty() ->
            validationFailure(field, "El $label es obligatorio")

        value.isBlank() ->
            validationFailure(field, "El $label no puede ser solo espacios")

        value != value.trim() ->
            validationFailure(field, "El $label no debe tener espacios al inicio ni al final")

        else -> DomainResult.Success(value)
    }
}
