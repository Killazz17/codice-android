package una.codice.core.data.network.syllabus

import kotlinx.serialization.Serializable
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus

/** `SyllabusDTO` del backend: el temario completo de un curso. Los ids son numéricos en el backend. */
@Serializable
data class SyllabusDto(
    val courseId: Long,
    val published: Boolean = false,
    val publishedAt: String? = null,
    val modules: List<ModuleDto> = emptyList(),
)

@Serializable
data class ModuleDto(
    val id: Long,
    val courseId: Long = 0,
    val number: Int,
    val title: String,
    val subtopics: List<SubtopicDto> = emptyList(),
)

@Serializable
data class SubtopicDto(
    val id: Long,
    val moduleId: Long = 0,
    val code: String,
    val title: String,
    val position: Int = 0,
    val objectives: List<ObjectiveDto> = emptyList(),
)

@Serializable
data class ObjectiveDto(
    val id: Long? = null,
    val description: String,
)

/** `ModuleCreateDTO`: el número es único en el curso. */
@Serializable
data class ModuleCreateRequestDto(
    val number: Int,
    val title: String,
)

/** `ModuleUpdateDTO`: lo que va en `null` no se cambia. */
@Serializable
data class ModuleUpdateRequestDto(
    val title: String? = null,
    val number: Int? = null,
)

/** `ModuleOrderDTO`: los ids de todos los módulos del curso, del primero al último. */
@Serializable
data class ModuleOrderRequestDto(
    val moduleIds: List<Long>,
)

/** `SubtopicCreateDTO`: el código es único en su módulo. */
@Serializable
data class SubtopicCreateRequestDto(
    val code: String,
    val title: String,
    val position: Int,
)

/** `SubtopicUpdateDTO`: lo que va en `null` no se cambia. */
@Serializable
data class SubtopicUpdateRequestDto(
    val code: String? = null,
    val title: String? = null,
    val position: Int? = null,
)

/** `ObjectiveCreateDTO`: la descripción del objetivo, de hasta 500 caracteres. */
@Serializable
data class ObjectiveRequestDto(
    val description: String,
)

/** De la respuesta de publicar la app solo necesita saber que el servidor lo aceptó. */
@Serializable
data class PublishDto(
    val published: Boolean = true,
    val publishedAt: String? = null,
)

/** El temario del servidor como modelo de dominio, con los ids del servidor y confianza total: no hay nada que dudar. */
fun SyllabusDto.toDomain(): Syllabus = Syllabus(
    courseId = courseId.toString(),
    modules = modules.sortedBy { it.number }.map { module ->
        CourseModule(
            id = module.id.toString(),
            number = module.number,
            title = module.title,
            subtopics = module.subtopics.sortedBy { it.position }.map { subtopic ->
                Subtopic(
                    id = subtopic.id.toString(),
                    code = subtopic.code,
                    title = subtopic.title,
                    position = subtopic.position,
                    objectives = subtopic.objectives.map { it.description },
                )
            },
        )
    },
    isPublished = published,
)
