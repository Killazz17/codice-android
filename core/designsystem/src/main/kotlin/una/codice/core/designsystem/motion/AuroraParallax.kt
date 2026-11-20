package una.codice.core.designsystem.motion

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * El puente del efecto parallax: la pantalla que se desplaza escribe aquí cuánto lleva y el fondo aurora lo lee para
 * moverse a otra velocidad que las tarjetas. [offsetPx] solo se lee al dibujar el fondo, así que cada fotograma del
 * desplazamiento redibuja el fondo sin recomponer nada.
 */
@Stable
class AuroraParallax {
    /** Cuánto se ha desplazado el contenido hacia arriba, en píxeles. 0 = sin desplazar. */
    var offsetPx by mutableFloatStateOf(0f)
}

/** El puente vigente; lo crea [una.codice.core.designsystem.component.AuroraBackground] para todo lo que contiene. */
val LocalAuroraParallax = compositionLocalOf { AuroraParallax() }

/**
 * Pone una pantalla con desplazamiento a mover el fondo aurora. Al salir de la pantalla el fondo vuelve a su sitio.
 * Se llama una vez dentro de la pantalla, con el mismo [ScrollState] que usa su `verticalScroll`.
 */
@Composable
fun AuroraParallaxEffect(scroll: ScrollState) {
    val parallax = LocalAuroraParallax.current
    LaunchedEffect(scroll, parallax) {
        snapshotFlow { scroll.value }.collect { parallax.offsetPx = it.toFloat() }
    }
    DisposableEffect(parallax) {
        onDispose { parallax.offsetPx = 0f }
    }
}
