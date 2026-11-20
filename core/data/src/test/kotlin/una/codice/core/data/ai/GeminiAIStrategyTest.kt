package una.codice.core.data.ai

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import una.codice.core.data.network.note.NoteApiService
import una.codice.core.data.network.note.TranscriptionBlockDto
import una.codice.core.data.network.note.TranscriptionPageDto
import una.codice.core.data.network.note.TranscriptionResultDto
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.result.DomainResult

/**
 * La IA corre en el backend: la estrategia solo manda la foto y traduce su respuesta. Si el backend falla, el error
 * llega tal cual; nunca se inventa una transcripción.
 */
class GeminiAIStrategyTest {

    private val photo = byteArrayOf(1, 2, 3)

    private fun resultado() = TranscriptionResultDto(
        proveedor = "gemini",
        modelo = "gemini-3.5-flash",
        promptVersion = "transcripcion-v1",
        textoCompleto = "Las corrutinas permiten código asíncrono\n\nEl dispatcher IO se usa para red",
        paginas = listOf(
            TranscriptionPageDto(
                indice = 0,
                bloques = listOf(
                    TranscriptionBlockDto("Las corrutinas permiten código asíncrono", 0.94, "parrafo", requiereRevision = false),
                    TranscriptionBlockDto("El dispatcher IO se usa para red", 0.41, "parrafo", requiereRevision = true),
                ),
            ),
        ),
        bloquesPorRevisar = 1,
    )

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun estrategia(api: NoteApiService) = GeminiAIStrategy(api)

    @Test
    fun `transcribe devuelve los bloques del backend con su confianza`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.transcribePhoto(any()) } returns resultado()

        val bloques = (estrategia(api).transcribe(photo, "apunte.jpg") as DomainResult.Success).data

        assertEquals(listOf("Las corrutinas permiten código asíncrono", "El dispatcher IO se usa para red"), bloques.map { it.text })
        assertEquals(listOf(0.94f, 0.41f), bloques.map { it.confidence })
        // El umbral de revisión del dominio (0,70) coincide con la marca del backend.
        assertEquals(listOf(false, true), bloques.map { it.requiresReview })
    }

    @Test
    fun `extractNote arma el apunte con el texto transcrito, sin clasificar y por revisar`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.transcribePhoto(any()) } returns resultado()

        val nota = (estrategia(api).extractNote("20", photo, "apunte.jpg") as DomainResult.Success).data

        assertEquals(resultado().textoCompleto, nota.confirmedText)
        assertEquals(null, nota.subtopicTitle)
        assertEquals(2, nota.transcription.size)
        assertTrue(nota.requiresReview)
        assertEquals(SyncStatus.QUEUED, nota.syncStatus)
    }

    @Test
    fun `si la IA no esta disponible se informa y no se inventa una transcripcion`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.transcribePhoto(any()) } throws httpError(503)
        val estrategia = estrategia(api)

        val transcripcion = estrategia.transcribe(photo, "apunte.jpg")
        val extraccion = estrategia.extractNote("20", photo, "apunte.jpg")

        listOf(transcripcion, extraccion).forEach { resultado ->
            val error = (resultado as DomainResult.Failure).error
            assertTrue(error.message, "IA no está disponible" in error.message)
        }
    }

    @Test
    fun `un archivo invalido y una foto muy pesada explican el problema`() = runTest {
        val api = mockk<NoteApiService>()
        val estrategia = estrategia(api)

        coEvery { api.transcribePhoto(any()) } throws httpError(400)
        val invalido = (estrategia.transcribe(photo, "tarea.pdf") as DomainResult.Failure).error
        coEvery { api.transcribePhoto(any()) } throws httpError(413)
        val pesada = (estrategia.transcribe(photo, "apunte.jpg") as DomainResult.Failure).error

        assertTrue(invalido.message, "foto válida" in invalido.message)
        assertTrue(pesada.message, "pesada" in pesada.message)
    }

    @Test
    fun `sin conexion es un error de red`() = runTest {
        val api = mockk<NoteApiService>()
        coEvery { api.transcribePhoto(any()) } throws IOException("sin internet")

        val error = (estrategia(api).transcribe(photo, "apunte.jpg") as DomainResult.Failure).error

        assertTrue(error is DomainError.Network)
    }

    @Test
    fun `el tipo de archivo que se declara sale de la extension`() = runTest {
        val api = mockk<NoteApiService>()
        val parte = slot<MultipartBody.Part>()
        coEvery { api.transcribePhoto(capture(parte)) } returns resultado()
        val estrategia = estrategia(api)

        val tipos = listOf("a.png", "a.WEBP", "a.pdf", "a.jpg", "sin_extension").map { nombre ->
            estrategia.transcribe(photo, nombre)
            parte.captured.body.contentType().toString()
        }

        assertEquals(listOf("image/png", "image/webp", "application/pdf", "image/jpeg", "image/jpeg"), tipos)
    }

    @Test
    fun `el contenido manda sobre el nombre del archivo`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()
        val pdf = "%PDF-1.7".toByteArray()

        // El selector de la galería entrega nombres sin extensión: antes un PNG o WebP salía declarado como JPEG y el
        // servidor lo rechazaba con "no es una foto válida".
        assertEquals("image/png", mediaTypeOf("image_3A1000000034", png).toString())
        assertEquals("image/webp", mediaTypeOf("1000000034", webp).toString())
        assertEquals("application/pdf", mediaTypeOf("tarea", pdf).toString())
        // Y si el nombre miente, gana lo que el archivo realmente es.
        assertEquals("image/jpeg", mediaTypeOf("captura.png", jpeg).toString())
        // Bytes desconocidos: se cae a la extensión, y sin ella a JPEG.
        assertEquals("image/png", mediaTypeOf("a.png", photo).toString())
        assertEquals("image/jpeg", mediaTypeOf("sin_extension", photo).toString())
    }
}
