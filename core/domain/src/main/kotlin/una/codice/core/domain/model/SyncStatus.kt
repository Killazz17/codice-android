package una.codice.core.domain.model

/**
 * Estado de sincronización de un apunte capturado con la cámara.
 *
 * El estudiante puede fotografiar apuntes sin conexión (issue "Cámara - cola
 * offline de capturas con WorkManager y Room", todavía sin construir); mientras
 * ese apunte no haya subido, la Biblioteca debe poder distinguirlo de uno ya
 * confirmado en el servidor. Es un `enum` y no un `Boolean` porque, a futuro,
 * un tercer estado (por ejemplo, "error al subir") no debería obligar a
 * renombrar un campo booleano por todo el código que ya lo usa.
 */
enum class SyncStatus {
    /** Ya se subió al servidor y la IA terminó de procesarlo. */
    SYNCED,

    /** Capturado en el dispositivo, pendiente de subir (sin conexión o en cola). */
    QUEUED,
}
