package una.codice.feature.syllabus.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import una.codice.core.domain.model.Enrollment
import una.codice.core.domain.model.GroupInviteInfo
import una.codice.core.domain.model.GroupStudent
import una.codice.core.domain.model.NewCourse
import una.codice.core.domain.model.NewGroup
import una.codice.core.domain.model.NewUser
import una.codice.core.domain.model.Session
import una.codice.core.domain.model.StudentGroup
import una.codice.core.domain.model.TeacherGroupSummary
import una.codice.core.domain.repository.AuthRepository
import una.codice.core.domain.repository.EnrollmentRepository
import una.codice.core.domain.result.DomainResult

/**
 * Los grupos de un profesor para probar cómo [SyllabusViewModel] arma su selector de cursos. Solo `observeTeacherGroups`
 * y la sesión hacen algo; el resto del contrato no lo usa este ViewModel.
 */
class FakeTeacherGroups(grupos: List<StudentGroup> = emptyList()) {

    val groups = MutableStateFlow(grupos.map { TeacherGroupSummary(it, 0, 0f, 0) })

    val auth: AuthRepository = object : AuthRepository {
        private val session = MutableStateFlow<Session?>(
            Session(token = "t", userId = "profe-1", role = una.codice.core.domain.model.UserRole.TEACHER),
        )

        override suspend fun login(email: String, password: String): DomainResult<Session> = error("No lo usa")

        override suspend fun register(newUser: NewUser): DomainResult<Session> = error("No lo usa")

        override fun currentSession(): Flow<Session?> = session

        override suspend fun logout(): DomainResult<Unit> = error("No lo usa")

        override suspend fun changePassword(currentPassword: String, newPassword: String): DomainResult<Unit> = error("No lo usa")
    }

    val enrollment: EnrollmentRepository = object : EnrollmentRepository {
        override suspend fun resolveInviteCode(code: String): DomainResult<StudentGroup> = error("No lo usa")

        override suspend fun requestJoin(code: String, studentId: String): DomainResult<Unit> = error("No lo usa")

        override fun observeMyEnrollments(): Flow<List<Enrollment>> = error("No lo usa")

        override fun observeTeacherGroups(teacherId: String): Flow<List<TeacherGroupSummary>> = groups

        override fun observeGroupInviteInfo(groupId: String): Flow<GroupInviteInfo> = error("No lo usa")

        override suspend fun regenerateInviteCode(groupId: String): DomainResult<GroupInviteInfo> = error("No lo usa")

        override suspend fun updateInviteSettings(groupId: String, requiresApproval: Boolean): DomainResult<Unit> =
            error("No lo usa")

        override fun observeGroupStudents(groupId: String): Flow<List<GroupStudent>> = error("No lo usa")

        override suspend fun decideEnrollment(enrollmentId: String, approve: Boolean): DomainResult<Unit> = error("No lo usa")

        override suspend fun createCourse(newCourse: NewCourse): DomainResult<GroupInviteInfo> = error("No lo usa")

        override suspend fun addGroup(courseId: String, newGroup: NewGroup): DomainResult<GroupInviteInfo> = error("No lo usa")

        override suspend fun deleteCourse(courseId: String): DomainResult<Unit> = error("No lo usa")

        override suspend fun leaveEnrollment(enrollmentId: String): DomainResult<Unit> = error("No lo usa")

        override suspend fun removeStudent(groupId: String, enrollmentId: String): DomainResult<Unit> = error("No lo usa")
    }
}
