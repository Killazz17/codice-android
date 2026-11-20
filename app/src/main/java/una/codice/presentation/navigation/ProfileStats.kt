package una.codice.presentation.navigation

import una.codice.core.domain.model.TeacherGroupSummary
import androidx.annotation.StringRes
import una.codice.R

/** Una cifra del bloque "Mi actividad" del Perfil. [value] es [UNKNOWN] mientras no se conoce o si no se pudo pedir. */
data class ProfileStat(val value: String, @StringRes val labelRes: Int, @StringRes val singularRes: Int? = null)

/**
 * Lo que muestra el bloque "Mi actividad": las cifras y, solo para el profesor, la cobertura promedio de sus grupos
 * ([coverage], de 0 a 1; nulo si no tiene grupos).
 */
data class ProfileActivity(
    val stats: List<ProfileStat> = emptyList(),
    val coverage: Float? = null,
)

const val UNKNOWN = "–"

/** Las cifras del profesor a partir de sus grupos; con `null` (no se pudieron pedir) todas quedan sin conocer. */
fun teacherActivity(groups: List<TeacherGroupSummary>?): ProfileActivity {
    if (groups == null) return ProfileActivity(stats = listOf(R.string.app_groups, R.string.app_students, R.string.app_requests).map { ProfileStat(UNKNOWN, it) })

    return ProfileActivity(
        stats = listOf(
            ProfileStat("${groups.size}", R.string.app_groups, R.string.app_groups_one),
            ProfileStat("${groups.sumOf { it.activeStudentCount }}", R.string.app_students, R.string.app_students_one),
            ProfileStat("${groups.sumOf { it.pendingRequestsCount }}", R.string.app_requests, R.string.app_requests_one),
        ),
        coverage = groups.takeIf { it.isNotEmpty() }?.map { it.averageCoverage }?.average()?.toFloat(),
    )
}

/** Las cifras del estudiante. Cada una es `null` si no se pudo pedir, y se muestra sin conocer. */
fun studentActivity(courses: Int?, notes: Int?, dueReviews: Int?): ProfileActivity = ProfileActivity(
    stats = listOf(
        ProfileStat(courses?.toString() ?: UNKNOWN, R.string.app_courses, R.string.app_courses_one),
        ProfileStat(notes?.toString() ?: UNKNOWN, R.string.app_notes, R.string.app_notes_one),
        ProfileStat(dueReviews?.toString() ?: UNKNOWN, R.string.app_reviews_today, R.string.app_reviews_today_one),
    ),
)
