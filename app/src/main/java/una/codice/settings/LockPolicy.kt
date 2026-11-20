package una.codice.settings

/**
 * ¿Hay que volver a pedir la huella cuando la persona regresa a la app después de dejarla en segundo plano?
 *
 * Al abrir la app de cero SIEMPRE se pide (eso no pasa por aquí). Esta función solo decide el regreso desde segundo
 * plano, y el equilibrio no es obvio: pedirla siempre protege más, pero molesta cada vez que se vuelve de la cámara,
 * del selector de fotos o de la hoja de compartir (todas dejan la app en segundo plano un instante).
 *
 * @param backgroundedAtMillis el reloj `SystemClock.elapsedRealtime()` cuando la app salió de pantalla.
 * @param nowMillis el mismo reloj, ahora que vuelve. Su resta es el tiempo que estuvo fuera.
 */
internal fun shouldRelock(backgroundedAtMillis: Long, nowMillis: Long): Boolean {
    // TODO(human): decidir cuánto tiempo fuera de la app justifica volver a pedir la huella (por ejemplo, 0 para pedirla
    //  siempre, o 30_000 / 60_000 para dar un margen). Por ahora no se vuelve a pedir al regresar: solo al abrir de cero.
    return false
}
