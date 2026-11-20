package una.codice.presentation.navigation

/**
 * Las rutas del `NavHost` único de la app.
 *
 * ### Por qué son `String` planos y no una clase sellada con `@Serializable`
 *
 * Navigation Compose soporta rutas tipadas (una clase por destino) desde
 * versiones recientes, pero ninguna de estas rutas necesita recibir
 * argumentos todavía — son puntos fijos del árbol de navegación. Strings
 * constantes son lo más simple que resuelve el problema real de hoy; si un
 * destino futuro necesita parámetros (por ejemplo, abrir un apunte por id),
 * ese es el momento de migrar esa ruta puntual a una clase tipada, no antes.
 */
object Routes {
    /** La pantalla de login (`:feature:auth`). Único punto de entrada sin sesión. */
    const val AUTH = "auth"

    /**
     * La pantalla de registro (`:feature:auth`), separada del login desde
     * que dejar de mezclar los dos flujos permitió agregar un campo real de
     * "confirmar contraseña". Se alcanza únicamente desde [AUTH].
     */
    const val REGISTER = "register"

    /**
     * Ruta "paraguas" del grafo de estudiante — no se muestra ella misma,
     * agrupa las pantallas que un estudiante puede visitar. Sirve como
     * destino de `navController.navigate(...)` tras el login/registro y
     * como ruta para `popUpTo` cuando haga falta limpiar todo ese grafo.
     */
    const val STUDENT_GRAPH = "student_graph"

    /** Misma idea que [STUDENT_GRAPH], para el lado del profesor. */
    const val TEACHER_GRAPH = "teacher_graph"

    /**
     * Primera pantalla real dentro del grafo de estudiante: la Biblioteca de
     * apuntes (`:feature:notes`, todavía sin construir — ver el placeholder
     * en `StudentPlaceholders.kt`).
     */
    const val STUDENT_NOTES = "student/notes"

    /**
     * Pestaña "Cursos" (`:feature:enrollment`): los cursos del estudiante, el estado de sus solicitudes y cómo unirse a
     * uno nuevo (código o QR). De cada curso se abre su mapa de cobertura.
     */
    const val STUDENT_ENROLLMENT = "student/enrollment"

    /**
     * Tab "Capturar" de la barra de navegación inferior (Figma, nodo
     * `tabbar` `24:114`). Placeholder: la pantalla real de cámara es un
     * issue aparte, todavía sin construir.
     */
    const val STUDENT_CAPTURE = "student/capture"

    /**
     * Mapa de cobertura de un curso (`:feature:coverage`). Ya no es una pestaña: se llega desde "Cursos" tocando un
     * curso, y la flecha de la pantalla regresa.
     */
    const val STUDENT_COVERAGE = "student/coverage"

    /** Tab "Repaso" de la barra de navegación inferior. Placeholder por ahora. */
    const val STUDENT_REVIEW = "student/review"

    /** Tab "Perfil" de la barra de navegación inferior. Placeholder por ahora. */
    const val STUDENT_PROFILE = "student/profile"

    /** Primera pantalla real dentro del grafo docente — también un placeholder por ahora. */
    const val TEACHER_HOME = "teacher/home"
    const val TEACHER_SYLLABUS = "teacher/syllabus"
    const val TEACHER_STUDENTS = "teacher/students"
    const val TEACHER_COVERAGE = "teacher/coverage"
    const val TEACHER_PROFILE = "teacher/profile"

    /** Pantalla para invitar estudiantes a un grupo específico. */
    const val TEACHER_INVITE = "teacher/invite/{groupId}"

    /** Crear un curso con su primer grupo; al terminar abre [TEACHER_INVITE] de ese grupo. */
    const val TEACHER_CREATE_COURSE = "teacher/courses/new"
}
