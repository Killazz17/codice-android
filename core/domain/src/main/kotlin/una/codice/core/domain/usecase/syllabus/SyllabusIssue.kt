package una.codice.core.domain.usecase.syllabus

/**
 * Un problema concreto encontrado en un temario antes de publicarlo.
 *
 * ### Por qué un tipo propio y no `DomainError.Validation`
 *
 * `DomainError.Validation` modela "este campo del formulario está mal" y
 * apunta a un `DomainError.Field` (correo, contraseña, código…). Un temario
 * no es un formulario de campos fijos: es un árbol donde el problema está en
 * *el módulo 4* o en *el subtema 4.4*, y hacen falta esas coordenadas para
 * poder señalarlo en pantalla. Meterlo a la fuerza en el enum compartido
 * obligaría a agregarle valores que solo esta pantalla usa.
 *
 * ### Bloqueante vs. advertencia
 *
 * [isBlocking] separa lo que impide publicar de lo que solo merece un aviso.
 * Un módulo repetido rompe la restricción `UNIQUE (course_id, number)` de la
 * base y no puede pasar; un subtema sin objetivos sí se puede publicar, pero
 * quedará sin poder auditarse — el profesor debe poder decidir con esa
 * información a la vista.
 */
sealed interface SyllabusIssue {

    /** Si impide publicar (`true`) o es solo una advertencia (`false`). */
    val isBlocking: Boolean

    /** Mensaje ya redactado en español, listo para pintarse tal cual. */
    val message: String

    /** El temario no tiene ni un módulo: no hay nada que publicar. */
    data object EmptySyllabus : SyllabusIssue {
        override val isBlocking = true
        override val message = "El temario no tiene ningún módulo todavía"
    }

    /** Dos módulos con el mismo número — rompe `UNIQUE (course_id, number)`. */
    data class DuplicateModuleNumber(val number: Int) : SyllabusIssue {
        override val isBlocking = true
        override val message = "El módulo $number está repetido"
    }

    /** Dos subtemas con el mismo código dentro del módulo — rompe `UNIQUE (module_id, code)`. */
    data class DuplicateSubtopicCode(val moduleNumber: Int, val code: String) : SyllabusIssue {
        override val isBlocking = true
        override val message = "El código $code está repetido en el módulo $moduleNumber"
    }

    /** Un módulo sin título: la columna `title` del DDL es `NOT NULL`. */
    data class BlankModuleTitle(val number: Int) : SyllabusIssue {
        override val isBlocking = true
        override val message = "El módulo $number no tiene título"
    }

    /** Un subtema sin título, mismo motivo que [BlankModuleTitle]. */
    data class BlankSubtopicTitle(val moduleNumber: Int, val code: String) : SyllabusIssue {
        override val isBlocking = true
        override val message = "El subtema $code del módulo $moduleNumber no tiene título"
    }

    /** Un módulo sin subtemas: no aporta nada al mapa de cobertura. */
    data class ModuleWithoutSubtopics(val number: Int) : SyllabusIssue {
        override val isBlocking = false
        override val message = "El módulo $number no tiene subtemas"
    }

    /**
     * Un subtema sin objetivos. No bloquea, pero la IA no podrá auditar
     * ningún apunte contra él — ver el KDoc de
     * [una.codice.core.domain.model.Subtopic].
     */
    data class SubtopicWithoutObjectives(val code: String) : SyllabusIssue {
        override val isBlocking = false
        override val message = "El subtema $code no tiene objetivos: no se podrá auditar"
    }
}
