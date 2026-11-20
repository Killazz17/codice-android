package una.codice.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlin.math.roundToInt

/**
 * Devuelve un número que sube de 0 hasta [target] al aparecer, y que se desliza al nuevo valor si [target] cambia.
 * Con las animaciones del sistema desactivadas devuelve [target] directamente.
 */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = CodiceMotion.EmphasizedMs): Int {
    val motion = rememberMotionEnabled()
    val shown = remember { Animatable(if (motion) 0f else target.toFloat()) }
    LaunchedEffect(target, motion) {
        if (motion) {
            shown.animateTo(target.toFloat(), tween(durationMs, easing = CodiceMotion.Settle))
        } else {
            shown.snapTo(target.toFloat())
        }
    }
    return shown.value.roundToInt()
}
