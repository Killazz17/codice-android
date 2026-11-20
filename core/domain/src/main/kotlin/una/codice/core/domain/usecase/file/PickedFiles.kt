package una.codice.core.domain.usecase.file

import java.io.ByteArrayOutputStream
import java.io.InputStream
import una.codice.core.domain.error.DomainError

/** El backend acepta archivos de hasta 10 MB (`spring.servlet.multipart.max-file-size`); más que eso responde 413. */
const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

private const val PDF_SIGNATURE = "%PDF-"
private const val PDF_SIGNATURE_WINDOW = 1024
private const val READ_BUFFER_BYTES = 8 * 1024

/**
 * Revisa en el teléfono, antes de subir nada, que el archivo del temario sea de verdad un PDF y no pase del tope.
 * Es la misma regla que aplica el backend (firma `%PDF-` en el primer KB): así un archivo equivocado se rechaza al
 * instante y no después de subirlo. Devuelve `null` si está bien.
 */
fun validateSyllabusPdf(bytes: ByteArray): DomainError? = when {
    bytes.isEmpty() -> DomainError.Unknown("El archivo está vacío. Elige el PDF del programa del curso")
    bytes.size > MAX_UPLOAD_BYTES -> DomainError.Unknown("El PDF pesa más de 10 MB. Comprímelo o súbelo por partes")
    !hasPdfSignature(bytes) -> DomainError.Unknown("El archivo no es un PDF válido. Elige el programa del curso en PDF")
    else -> null
}

private fun hasPdfSignature(bytes: ByteArray): Boolean {
    val window = String(bytes, 0, minOf(bytes.size, PDF_SIGNATURE_WINDOW), Charsets.ISO_8859_1)
    return window.contains(PDF_SIGNATURE)
}

/**
 * Lee el archivo completo pero se detiene al pasar [max] bytes: devuelve `null` si es más grande. Así un archivo enorme
 * elegido por error no llena la memoria del teléfono.
 */
fun InputStream.readAtMost(max: Int = MAX_UPLOAD_BYTES): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > max) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/**
 * El nombre con el que se manda el archivo al servidor: solo letras, números, punto, guion y guion bajo, sin carpetas ni
 * `..`. El nombre lo escribe quien guardó el archivo (o el `content://` del selector) y no se debe confiar en él.
 */
fun safeFileName(raw: String?, fallback: String): String {
    val name = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .replace(Regex("\\.{2,}"), ".")
        .trim('.', '_')
        .take(100)
    return name.ifBlank { fallback }
}
