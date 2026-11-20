package una.codice.core.domain.model

/**
 * Los dos únicos roles que existen en Códice.
 *
 * En Códice **no hay administrador**: profesor y estudiante usan exactamente la
 * misma app instalada, y lo único que cambia entre ellos es la barra inferior de
 * navegación y qué acciones tienen permitidas.
 *
 * ### Por qué es un `enum` y no un `String`
 *
 * El issue #2 pide como regla "el rol debe ser STUDENT o TEACHER". Si el rol
 * fuera un `String`, esa regla habría que validarla a mano en cada sitio donde
 * se use, y bastaría un error de dedo (`"STUDNET"`) para meter un dato inválido
 * en la base.
 *
 * Con un `enum` el problema desaparece: el compilador solo acepta uno de estos
 * dos valores. La validación deja de ser código y pasa a ser el tipo.
 */
enum class UserRole {
    /**
     * Estudiante: dueño de sus apuntes.
     * Fotografía apuntes, confirma la transcripción, ve su mapa de cobertura
     * personal y resuelve los quizzes generados sobre sus vacíos.
     */
    STUDENT,

    /**
     * Profesor: dueño y administrador de sus cursos.
     * Publica el temario, genera los códigos de invitación, aprueba matrículas
     * y ve la cobertura del grupo de forma **agregada y anónima** — nunca los
     * apuntes de un estudiante concreto.
     */
    TEACHER,
}
