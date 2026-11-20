package una.codice.feature.notes.presentation.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import una.codice.feature.notes.R

class CameraImageTest {

    @Test
    fun `una foto chica no se reduce`() {
        assertEquals(1, sampleSizeFor(width = 1600, height = 1200))
        assertEquals(1, sampleSizeFor(width = 2048, height = 1536))
    }

    @Test
    fun `una foto de 12 megapixeles se queda como esta porque reducirla la dejaria bajo el minimo`() {
        // 4000 / 2 = 2000, menos de los 2048 px que se quieren conservar.
        assertEquals(1, sampleSizeFor(width = 4000, height = 3000))
    }

    @Test
    fun `una foto de 48 megapixeles se reduce a la mitad`() {
        // 8000 / 2 = 4000 (alcanza), pero 8000 / 4 = 2000 ya no.
        assertEquals(2, sampleSizeFor(width = 8000, height = 6000))
    }

    @Test
    fun `una foto enorme se reduce por potencias de dos`() {
        assertEquals(4, sampleSizeFor(width = 16000, height = 12000))
    }

    // Una foto en vertical tiene el lado largo en el alto.
    @Test
    fun `usa el lado largo aunque la foto este en vertical`() {
        assertEquals(sampleSizeFor(width = 8000, height = 6000), sampleSizeFor(width = 6000, height = 8000))
    }

    @Test
    fun `el lado largo reducido nunca baja del minimo salvo que ya fuera menor`() {
        val ejemplos = listOf(1000 to 800, 2048 to 1000, 3000 to 2000, 4000 to 3000, 8000 to 6000, 12000 to 9000)
        ejemplos.forEach { (ancho, alto) ->
            val reducido = maxOf(ancho, alto) / sampleSizeFor(ancho, alto)
            assertTrue("$ancho x $alto quedó en $reducido", reducido >= minOf(maxOf(ancho, alto), UPLOAD_MIN_LONG_EDGE_PX))
        }
    }

    @Test
    fun `el contador de paginas habla en singular y plural`() {
        assertEquals(R.string.notes_place_the_note_in_view_and_take_the_ph, pagesLabelRes(0))
        assertEquals(R.string.notes_1_page_taken, pagesLabelRes(1))
        assertEquals(R.string.notes_pages_taken, pagesLabelRes(3))
    }
}
