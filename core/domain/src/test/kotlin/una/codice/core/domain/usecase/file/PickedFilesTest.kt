package una.codice.core.domain.usecase.file

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickedFilesTest {

    private val pdf = "%PDF-1.7\n1 0 obj".toByteArray()

    @Test
    fun `un PDF de verdad pasa la validacion`() {
        assertNull(validateSyllabusPdf(pdf))
    }

    @Test
    fun `un archivo vacio se rechaza`() {
        assertTrue(validateSyllabusPdf(ByteArray(0))!!.message.contains("vacío"))
    }

    @Test
    fun `una imagen o un documento que no es PDF se rechaza antes de subirlo`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A) + ByteArray(100)

        assertTrue(validateSyllabusPdf(png)!!.message.contains("no es un PDF"))
        assertTrue(validateSyllabusPdf("hola, esto es texto".toByteArray())!!.message.contains("no es un PDF"))
    }

    @Test
    fun `un PDF de mas de 10 MB se rechaza`() {
        val grande = pdf + ByteArray(MAX_UPLOAD_BYTES)

        assertTrue(validateSyllabusPdf(grande)!!.message.contains("10 MB"))
    }

    @Test
    fun `la firma de PDF puede venir despues de unos bytes de basura, como acepta el backend`() {
        assertNull(validateSyllabusPdf("﻿\n".toByteArray() + pdf))
    }

    @Test
    fun `readAtMost lee un archivo que cabe y corta el que no`() {
        assertEquals(5, ByteArrayInputStream(ByteArray(5)).readAtMost(10)!!.size)
        assertNotNull(ByteArrayInputStream(ByteArray(10)).readAtMost(10))
        assertNull(ByteArrayInputStream(ByteArray(11)).readAtMost(10))
    }

    @Test
    fun `el nombre del archivo se limpia de carpetas y caracteres raros`() {
        assertEquals("programa.pdf", safeFileName("../../etc/programa.pdf", "x.pdf"))
        assertEquals("mi_programa__final_.pdf", safeFileName("mi programa (final).pdf", "x.pdf"))
        assertEquals("x.pdf", safeFileName("...", "x.pdf"))
        assertEquals("x.pdf", safeFileName(null, "x.pdf"))
        assertEquals("programa.pdf", safeFileName("content://media/documents/programa.pdf", "x.pdf"))
    }
}
