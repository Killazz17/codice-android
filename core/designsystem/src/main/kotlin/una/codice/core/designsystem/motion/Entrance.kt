package una.codice.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Hace que un elemento de una lista entre subiendo y apareciendo, con una pausa proporcional a su posición: así la
 * lista "se despliega" en cascada en vez de aparecer de golpe. Solo se reproduce la primera vez que el elemento se
 * muestra: al volver a desplazarse sobre él no repite la animación.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val motion = rememberMotionEnabled()
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played || !motion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!played && motion) {
            delay(index.coerceIn(0, CodiceMotion.MaxStaggered) * CodiceMotion.StaggerMs)
            progress.animateTo(1f, tween(CodiceMotion.EmphasizedMs, easing = CodiceMotion.Settle))
        }
        played = true
    }

    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}
