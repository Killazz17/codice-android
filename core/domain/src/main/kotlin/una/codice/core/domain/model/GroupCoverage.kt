package una.codice.core.domain.model

/**
 * Cobertura agregada y anónima de un grupo, lo que ve su profesor. Ningún campo identifica a un
 * estudiante: solo qué porcentaje del grupo tiene cada subtema cubierto, parcial o sin apuntes.
 *
 * @property modules Ordenados por número de módulo, con sus subtemas en el orden del temario.
 */
data class GroupCoverage(
    val totalStudents: Int,
    val modules: List<GroupModuleCoverage>,
) {
    /** Promedio de [GroupSubtopicCoverage.coveredPercentage] de todos los subtemas, de 0 a 100. */
    val averageCoveredPercentage: Double
        get() {
            val subtopics = modules.flatMap { it.subtopics }
            if (subtopics.isEmpty()) return 0.0
            return subtopics.sumOf { it.coveredPercentage } / subtopics.size
        }
}

data class GroupModuleCoverage(
    val moduleNumber: Int,
    val moduleTitle: String,
    val subtopics: List<GroupSubtopicCoverage>,
) {
    /** Promedio de [GroupSubtopicCoverage.coveredPercentage] de los subtemas del módulo, de 0 a 100. */
    val averageCoveredPercentage: Double
        get() = if (subtopics.isEmpty()) 0.0 else subtopics.sumOf { it.coveredPercentage } / subtopics.size
}

/** Porcentajes del grupo para un subtema, de 0 a 100; los tres suman 100. */
data class GroupSubtopicCoverage(
    val subtopicId: String,
    val subtopicCode: String,
    val subtopicTitle: String,
    val coveredPercentage: Double,
    val partialPercentage: Double,
    val noNotesPercentage: Double,
)
