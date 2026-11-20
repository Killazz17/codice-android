package una.codice.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.network.syllabus.ModuleCreateRequestDto
import una.codice.core.data.network.syllabus.ModuleDto
import una.codice.core.data.network.syllabus.ModuleOrderRequestDto
import una.codice.core.data.network.syllabus.ModuleUpdateRequestDto
import una.codice.core.data.network.syllabus.ObjectiveDto
import una.codice.core.data.network.syllabus.ObjectiveRequestDto
import una.codice.core.data.network.syllabus.PublishDto
import una.codice.core.data.network.syllabus.SubtopicCreateRequestDto
import una.codice.core.data.network.syllabus.SubtopicDto
import una.codice.core.data.network.syllabus.SubtopicUpdateRequestDto
import una.codice.core.data.network.syllabus.SyllabusApiService
import una.codice.core.data.network.syllabus.SyllabusDto
import una.codice.core.data.network.teacher.TeacherApiService
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CourseModule
import una.codice.core.domain.model.Subtopic
import una.codice.core.domain.model.Syllabus
import una.codice.core.domain.result.DomainResult

/**
 * Publicar el temario (#37): el borrador vive en el teléfono y solo al publicar se escribe en el servidor, reconociendo
 * lo que ya existe para no duplicarlo.
 */
class CourseRepositorySyllabusTest {

    private val api = mockk<SyllabusApiService>()
    private val repositorio = CourseRepositoryImpl(mockk<TeacherApiService>(), api)

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun subtemaDto(id: Long, code: String, title: String, position: Int, vararg objetivos: String) =
        SubtopicDto(id, 100, code, title, position, objetivos.mapIndexed { i, d -> ObjectiveDto(id * 10 + i, d) })

    private fun moduloDto(id: Long, number: Int, title: String, vararg subtemas: SubtopicDto) =
        ModuleDto(id, 25, number, title, subtemas.toList())

    private fun servidor(vararg modulos: ModuleDto, published: Boolean = false) =
        SyllabusDto(courseId = 25, published = published, modules = modulos.toList())

    private fun subtema(code: String, title: String, position: Int, vararg objetivos: String) =
        Subtopic(id = "s-$code", code = code, title = title, position = position, objectives = objetivos.toList())

    private fun modulo(number: Int, title: String, vararg subtemas: Subtopic) =
        CourseModule(id = "m-$number", number = number, title = title, subtopics = subtemas.toList())

    private fun borrador(vararg modulos: CourseModule) = Syllabus(courseId = "25", modules = modulos.toList())

    /** Las escrituras responden como el backend: un id para lo creado. */
    private fun stubEscrituras() {
        coEvery { api.createModule(any(), any()) } answers {
            val pedido = secondArg<ModuleCreateRequestDto>()
            ModuleDto(id = 100L + pedido.number, courseId = 25, number = pedido.number, title = pedido.title)
        }
        coEvery { api.createSubtopic(any(), any()) } answers {
            val pedido = secondArg<SubtopicCreateRequestDto>()
            SubtopicDto(id = 1000L + pedido.position, moduleId = firstArg(), code = pedido.code, title = pedido.title, position = pedido.position)
        }
        coEvery { api.addObjective(any(), any()) } answers { ObjectiveDto(id = 1, description = secondArg<ObjectiveRequestDto>().description) }
        coEvery { api.updateSubtopic(any(), any()) } answers { subtemaDto(firstArg(), "x", "x", 0) }
        coEvery { api.deleteSubtopic(any()) } returns Unit
        coEvery { api.deleteObjective(any()) } returns Unit
        coEvery { api.updateModule(any(), any()) } answers { ModuleDto(id = firstArg(), courseId = 25, number = 1, title = "x") }
        coEvery { api.deleteModule(any()) } returns Unit
        coEvery { api.reorderModules(any(), any()) } returns emptyList()
        coEvery { api.publish(any()) } returns PublishDto()
    }

    // --- el borrador no toca el servidor ---

    @Test
    fun `guardar el borrador no escribe nada en el servidor`() = runTest {
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0))))

        coVerify(exactly = 0) { api.createModule(any(), any()) }
        coVerify(exactly = 0) { api.createSubtopic(any(), any()) }
        coVerify(exactly = 0) { api.publish(any()) }
    }

    // --- primera publicación ---

    @Test
    fun `la primera publicacion crea modulos, subtemas y objetivos en orden y al final publica`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor()
        repositorio.saveDraft(
            borrador(
                modulo(2, "Servicios", subtema("2.1", "REST", 0)),
                modulo(1, "Intro", subtema("1.1", "Kotlin", 0, "Escribir funciones", "Usar clases"), subtema("1.2", "Android", 1)),
            ),
        )

        val resultado = repositorio.publish("25")

        assertEquals(DomainResult.Success(Unit), resultado)
        coVerifyOrder {
            api.createModule(25L, ModuleCreateRequestDto(1, "Intro"))
            api.createSubtopic(101L, SubtopicCreateRequestDto("1.1", "Kotlin", 0))
            api.addObjective(1000L, ObjectiveRequestDto("Escribir funciones"))
            api.addObjective(1000L, ObjectiveRequestDto("Usar clases"))
            api.createSubtopic(101L, SubtopicCreateRequestDto("1.2", "Android", 1))
            api.createModule(25L, ModuleCreateRequestDto(2, "Servicios"))
            api.createSubtopic(102L, SubtopicCreateRequestDto("2.1", "REST", 0))
            api.publish(25L)
        }
    }

    @Test
    fun `tras publicar el temario queda como publicado con lo que tiene el servidor`() = runTest {
        stubEscrituras()
        val publicado = servidor(moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0)), published = true)
        coEvery { api.getSyllabus(25L) } returnsMany listOf(servidor(), publicado)
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0))))

        repositorio.publish("25")

        val temario = repositorio.observeSyllabus("25").first()
        assertTrue(temario.isPublished)
        assertEquals("101", temario.modules.single().id)
    }

    // --- republicar ---

    @Test
    fun `publicar dos veces el mismo temario no duplica nada`() = runTest {
        stubEscrituras()
        val igual = servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Kotlin", 0, "Escribir funciones"), subtemaDto(1001, "1.2", "Android", 1)),
            published = true,
        )
        coEvery { api.getSyllabus(25L) } returns igual
        repositorio.saveDraft(
            borrador(modulo(1, "Intro", subtema("1.1", "Kotlin", 0, "Escribir funciones"), subtema("1.2", "Android", 1))),
        )

        val resultado = repositorio.publish("25")

        assertEquals(DomainResult.Success(Unit), resultado)
        coVerify(exactly = 0) { api.createModule(any(), any()) }
        coVerify(exactly = 0) { api.createSubtopic(any(), any()) }
        coVerify(exactly = 0) { api.updateSubtopic(any(), any()) }
        coVerify(exactly = 0) { api.deleteSubtopic(any()) }
        coVerify(exactly = 0) { api.addObjective(any(), any()) }
        coVerify(exactly = 0) { api.deleteObjective(any()) }
        coVerify(exactly = 0) { api.updateModule(any(), any()) }
        coVerify(exactly = 0) { api.deleteModule(any()) }
        coVerify(exactly = 0) { api.reorderModules(any(), any()) }
        coVerify(exactly = 1) { api.publish(25L) }
    }

    @Test
    fun `un subtema nuevo se crea y uno con otro titulo o posicion se actualiza`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Viejo", 0), subtemaDto(1001, "1.2", "Igual", 1)),
        )
        repositorio.saveDraft(
            borrador(
                modulo(
                    1, "Intro",
                    subtema("1.2", "Igual", 0),
                    subtema("1.1", "Nuevo título", 1),
                    subtema("1.3", "Agregado", 2),
                ),
            ),
        )

        repositorio.publish("25")

        coVerify { api.updateSubtopic(1000L, SubtopicUpdateRequestDto(title = "Nuevo título", position = 1)) }
        coVerify { api.updateSubtopic(1001L, SubtopicUpdateRequestDto(title = null, position = 0)) }
        coVerify { api.createSubtopic(101L, SubtopicCreateRequestDto("1.3", "Agregado", 2)) }
        coVerify(exactly = 0) { api.createModule(any(), any()) }
    }

    @Test
    fun `un subtema que ya no esta en el borrador se borra`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Queda", 0), subtemaDto(1001, "1.2", "Se va", 1)),
        )
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Queda", 0))))

        repositorio.publish("25")

        coVerify { api.deleteSubtopic(1001L) }
        coVerify(exactly = 0) { api.deleteSubtopic(1000L) }
    }

    // Lo que se quita va primero: si un subtema ya tiene apuntes el servidor lo rechaza y todavía no se escribió nada.
    @Test
    fun `si un subtema a quitar tiene apuntes se avisa y no se escribe nada mas`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Queda", 0), subtemaDto(1001, "1.2", "Con apuntes", 1)),
        )
        coEvery { api.deleteSubtopic(1001L) } throws httpError(422)
        repositorio.saveDraft(
            borrador(modulo(1, "Intro", subtema("1.1", "Queda", 0), subtema("1.3", "Nuevo", 1)), modulo(2, "Otro", subtema("2.1", "X", 0))),
        )

        val mensaje = (repositorio.publish("25") as DomainResult.Failure).error.message

        assertTrue(mensaje, "1.2" in mensaje && "apuntes" in mensaje)
        coVerify(exactly = 0) { api.createModule(any(), any()) }
        coVerify(exactly = 0) { api.createSubtopic(any(), any()) }
        coVerify(exactly = 0) { api.publish(any()) }
    }

    @Test
    fun `los objetivos que sobran se borran y los que faltan se agregan`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0, "Se queda", "Sobra")),
        )
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0, "Se queda", "  Falta  ", ""))))

        repositorio.publish("25")

        coVerify { api.deleteObjective(10001L) }
        coVerify(exactly = 0) { api.deleteObjective(10000L) }
        coVerify { api.addObjective(1000L, ObjectiveRequestDto("Falta")) }
        coVerify(exactly = 1) { api.addObjective(any(), any()) }
    }

    // --- renombrar, quitar y mover módulos ---

    /** Un módulo del borrador con los ids del servidor, como queda después de `loadSyllabus`. */
    private fun moduloDelServidor(id: Long, number: Int, title: String, vararg subtemas: Subtopic) =
        CourseModule(id = id.toString(), number = number, title = title, subtopics = subtemas.toList())

    private fun subtemaDelServidor(id: Long, code: String, title: String, position: Int) =
        Subtopic(id = id.toString(), code = code, title = title, position = position, objectives = emptyList())

    @Test
    fun `renombrar un modulo que ya esta en el servidor lo actualiza y no lo vuelve a crear`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0)))
        repositorio.saveDraft(borrador(modulo(1, "Otro nombre", subtema("1.1", "Tema", 0))))

        val resultado = repositorio.publish("25")

        assertEquals(DomainResult.Success(Unit), resultado)
        coVerify { api.updateModule(101L, ModuleUpdateRequestDto(title = "Otro nombre")) }
        coVerify(exactly = 0) { api.createModule(any(), any()) }
        coVerify(exactly = 0) { api.reorderModules(any(), any()) }
        coVerify(exactly = 1) { api.publish(25L) }
    }

    @Test
    fun `un modulo que ya no esta en el borrador se borra antes de escribir lo nuevo`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0)),
            moduloDto(102, 2, "Se quiere quitar", subtemaDto(1001, "2.1", "X", 0)),
        )
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0), subtema("1.2", "Nuevo", 1))))

        val resultado = repositorio.publish("25")

        assertEquals(DomainResult.Success(Unit), resultado)
        coVerifyOrder {
            api.deleteModule(102L)
            api.createSubtopic(101L, SubtopicCreateRequestDto("1.2", "Nuevo", 1))
            api.publish(25L)
        }
        // Sus subtemas se van con el módulo: no se borran uno por uno.
        coVerify(exactly = 0) { api.deleteSubtopic(any()) }
    }

    @Test
    fun `si un modulo a quitar tiene subtemas con apuntes se avisa y no se escribe nada mas`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0)),
            moduloDto(102, 2, "Con apuntes", subtemaDto(1001, "2.1", "X", 0)),
        )
        coEvery { api.deleteModule(102L) } throws httpError(422)
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0), subtema("1.2", "Nuevo", 1))))

        val mensaje = (repositorio.publish("25") as DomainResult.Failure).error.message

        assertTrue(mensaje, "módulo 2" in mensaje && "apuntes" in mensaje)
        coVerify(exactly = 0) { api.createSubtopic(any(), any()) }
        coVerify(exactly = 0) { api.publish(any()) }
    }

    @Test
    fun `mover un modulo reordena con un solo PUT y ajusta los codigos de sus subtemas sin borrar nada`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0)),
            moduloDto(102, 2, "Servicios", subtemaDto(1001, "2.1", "REST", 0)),
        )
        // Servicios pasó al 1 y Intro al 2, con el prefijo de sus códigos ajustado, como hace moveModule.
        repositorio.saveDraft(
            borrador(
                moduloDelServidor(102, 1, "Servicios", subtemaDelServidor(1001, "1.1", "REST", 0)),
                moduloDelServidor(101, 2, "Intro", subtemaDelServidor(1000, "2.1", "Tema", 0)),
            ),
        )

        val resultado = repositorio.publish("25")

        assertEquals(DomainResult.Success(Unit), resultado)
        coVerify { api.updateSubtopic(1001L, SubtopicUpdateRequestDto(code = "1.1")) }
        coVerify { api.updateSubtopic(1000L, SubtopicUpdateRequestDto(code = "2.1")) }
        coVerify(exactly = 1) { api.reorderModules(25L, ModuleOrderRequestDto(listOf(102L, 101L))) }
        coVerify(exactly = 0) { api.createModule(any(), any()) }
        coVerify(exactly = 0) { api.deleteModule(any()) }
        coVerify(exactly = 0) { api.createSubtopic(any(), any()) }
        coVerify(exactly = 0) { api.deleteSubtopic(any()) }
        coVerify(exactly = 0) { api.updateModule(any(), any()) }
        coVerify(exactly = 1) { api.publish(25L) }
    }

    @Test
    fun `un modulo nuevo en medio se crea y el PUT de orden lo coloca sin chocar con los numeros`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0)),
            moduloDto(102, 2, "Servicios", subtemaDto(1001, "2.1", "REST", 0)),
        )
        repositorio.saveDraft(
            borrador(
                moduloDelServidor(101, 1, "Intro", subtemaDelServidor(1000, "1.1", "Tema", 0)),
                modulo(2, "Nuevo", subtema("2.1", "Recién llegado", 0)),
                moduloDelServidor(102, 3, "Servicios", subtemaDelServidor(1001, "3.1", "REST", 0)),
            ),
        )

        val resultado = repositorio.publish("25")

        assertEquals(DomainResult.Success(Unit), resultado)
        // El 2 ya lo tiene Servicios en el servidor: el nuevo entra por encima de todos (4) y el PUT lo acomoda.
        coVerify(exactly = 1) { api.createModule(25L, ModuleCreateRequestDto(4, "Nuevo")) }
        coVerify(exactly = 1) { api.reorderModules(25L, ModuleOrderRequestDto(listOf(101L, 104L, 102L))) }
        coVerify { api.updateSubtopic(1001L, SubtopicUpdateRequestDto(code = "3.1")) }
        coVerify(exactly = 0) { api.deleteModule(any()) }
    }

    @Test
    fun `un 422 al reordenar pide volver a abrir el curso`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(101, 1, "Intro", subtemaDto(1000, "1.1", "Tema", 0)),
            moduloDto(102, 2, "Servicios", subtemaDto(1001, "2.1", "REST", 0)),
        )
        coEvery { api.reorderModules(any(), any()) } throws httpError(422)
        repositorio.saveDraft(
            borrador(
                moduloDelServidor(102, 1, "Servicios", subtemaDelServidor(1001, "1.1", "REST", 0)),
                moduloDelServidor(101, 2, "Intro", subtemaDelServidor(1000, "2.1", "Tema", 0)),
            ),
        )

        val mensaje = (repositorio.publish("25") as DomainResult.Failure).error.message

        assertTrue(mensaje, "orden de los módulos" in mensaje)
        coVerify(exactly = 0) { api.publish(any()) }
    }

    // --- errores ---

    @Test
    fun `sin borrador no hay nada que publicar`() = runTest {
        val resultado = repositorio.publish("25")

        assertEquals(DomainResult.Failure(DomainError.Unknown("No hay un temario para publicar")), resultado)
        coVerify(exactly = 0) { api.getSyllabus(any()) }
    }

    @Test
    fun `un id de curso que no es numerico no llama al servidor`() = runTest {
        repositorio.saveDraft(Syllabus(courseId = "EIF411", modules = listOf(modulo(1, "Intro"))))

        assertTrue((repositorio.publish("EIF411") as DomainResult.Failure).error is DomainError.Unknown)
        assertTrue((repositorio.loadSyllabus("EIF411") as DomainResult.Failure).error is DomainError.Unknown)
        coVerify(exactly = 0) { api.getSyllabus(any()) }
    }

    @Test
    fun `un 409 al crear explica que el temario cambio en el servidor y no publica`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor()
        coEvery { api.createModule(any(), any()) } throws httpError(409)
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0))))

        val mensaje = (repositorio.publish("25") as DomainResult.Failure).error.message

        assertTrue(mensaje, "cambió en el servidor" in mensaje)
        coVerify(exactly = 0) { api.publish(any()) }
    }

    @Test
    fun `un dato demasiado largo se explica con los limites del servidor`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor()
        coEvery { api.addObjective(any(), any()) } throws httpError(400)
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0, "Objetivo"))))

        val mensaje = (repositorio.publish("25") as DomainResult.Failure).error.message

        assertTrue(mensaje, "255" in mensaje && "500" in mensaje)
    }

    @Test
    fun `sin conexion al publicar es un error de red y no se escribe nada`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } throws IOException("sin internet")
        repositorio.saveDraft(borrador(modulo(1, "Intro", subtema("1.1", "Tema", 0))))

        assertEquals(DomainResult.Failure(DomainError.Network()), repositorio.publish("25"))
        coVerify(exactly = 0) { api.createModule(any(), any()) }
    }

    @Test
    fun `un 422 al publicar dice que el temario no tiene subtemas`() = runTest {
        stubEscrituras()
        coEvery { api.getSyllabus(25L) } returns servidor()
        coEvery { api.publish(any()) } throws httpError(422)
        repositorio.saveDraft(borrador(modulo(1, "Intro")))

        val mensaje = (repositorio.publish("25") as DomainResult.Failure).error.message

        assertTrue(mensaje, "sin subtemas" in mensaje)
    }

    // --- leer el temario ---

    @Test
    fun `loadSyllabus trae el temario del servidor ordenado y marca si esta publicado`() = runTest {
        coEvery { api.getSyllabus(25L) } returns servidor(
            moduloDto(102, 2, "Segundo", subtemaDto(1002, "2.1", "B", 0)),
            moduloDto(101, 1, "Primero", subtemaDto(1001, "1.2", "Y", 1, "Obj"), subtemaDto(1000, "1.1", "X", 0)),
            published = true,
        )

        val temario = (repositorio.loadSyllabus("25") as DomainResult.Success).data

        assertEquals("25", temario.courseId)
        assertTrue(temario.isPublished)
        assertEquals(listOf(1, 2), temario.modules.map { it.number })
        assertEquals(listOf("1.1", "1.2"), temario.modules.first().subtopics.map { it.code })
        assertEquals(listOf("Obj"), temario.modules.first().subtopics.last().objectives)
        assertFalse(temario.modules.first().isLowConfidence)
    }

    @Test
    fun `loadSyllabus deja lo leido como borrador si todavia no habia uno`() = runTest {
        coEvery { api.getSyllabus(25L) } returns servidor(moduloDto(101, 1, "Primero", subtemaDto(1000, "1.1", "X", 0)))

        repositorio.loadSyllabus("25")

        assertEquals("Primero", repositorio.observeSyllabus("25").first().modules.single().title)
    }

    @Test
    fun `loadSyllabus no pisa un borrador que el profesor ya empezo`() = runTest {
        coEvery { api.getSyllabus(25L) } returns servidor(moduloDto(101, 1, "Del servidor", subtemaDto(1000, "1.1", "X", 0)))
        repositorio.saveDraft(borrador(modulo(1, "Mi borrador", subtema("1.1", "Mío", 0))))

        val delServidor = (repositorio.loadSyllabus("25") as DomainResult.Success).data

        assertEquals("Del servidor", delServidor.modules.single().title)
        assertEquals("Mi borrador", repositorio.observeSyllabus("25").first().modules.single().title)
    }

    @Test
    fun `si no se pudo leer el temario se dice y no se confunde con un curso sin temario`() = runTest {
        coEvery { api.getSyllabus(25L) } throws httpError(403)

        val error = (repositorio.loadSyllabus("25") as DomainResult.Failure).error.message

        assertTrue(error, "profesor dueño" in error)
        assertTrue(repositorio.observeSyllabus("25").first().modules.isEmpty())
    }

    // --- observeSyllabus no inventa ---

    @Test
    fun `un curso sin borrador muestra un temario vacio y no uno inventado ni de otro curso`() = runTest {
        repositorio.saveDraft(Syllabus(courseId = "77", modules = listOf(modulo(1, "De otro curso"))))

        val temario = repositorio.observeSyllabus("25").first()

        assertEquals(Syllabus(courseId = "25"), temario)
    }
}
