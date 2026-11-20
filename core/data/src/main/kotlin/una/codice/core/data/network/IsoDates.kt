package una.codice.core.data.network

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val ISO_INSTANT = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?Z$""")

/**
 * Convierte una fecha ISO-8601 en UTC del backend (`2026-10-12T15:00:00.123456Z`) a milisegundos.
 *
 * No usa `java.time` porque necesita API 26 (o desugaring) y la app soporta desde la 24.
 * `SimpleDateFormat` no entiende más de 3 decimales, así que la fracción se recorta a milisegundos
 * a mano. Si el texto no tiene ese formato devuelve 0 en vez de reventar la pantalla.
 */
fun isoInstantToEpochMillis(value: String): Long {
    val match = ISO_INSTANT.matchEntire(value.trim()) ?: return 0L
    val (seconds, fraction) = match.destructured
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val base = runCatching { format.parse(seconds)?.time }.getOrNull() ?: return 0L
    val millis = fraction.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
    return base + millis
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/**
 * Cuánto pasó desde [epochMillis], como lo muestran las solicitudes del profesor: "hace 5 min",
 * "hace 2 h", "ayer", "hace 3 días". Vacío si la fecha no se pudo leer (0).
 */
fun relativeTimeFrom(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0L) return ""
    val elapsed = (now - epochMillis).coerceAtLeast(0L)
    return when {
        elapsed < MINUTE_MS -> "ahora"
        elapsed < HOUR_MS -> "hace ${elapsed / MINUTE_MS} min"
        elapsed < DAY_MS -> "hace ${elapsed / HOUR_MS} h"
        elapsed < 2 * DAY_MS -> "ayer"
        else -> "hace ${elapsed / DAY_MS} días"
    }
}

/** Fecha corta en la zona del teléfono, p. ej. "13/11/2026". Vacío si la fecha no se pudo leer (0). */
fun shortDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date(epochMillis))
}
