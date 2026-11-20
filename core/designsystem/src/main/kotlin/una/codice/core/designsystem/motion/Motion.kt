package una.codice.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Duraciones y curvas compartidas por todas las animaciones de la app, para que se sientan como una sola.
 */
object CodiceMotion {
    const val QuickMs = 180
    const val StandardMs = 350
    const val EmphasizedMs = 700

    /** Pausa entre un elemento de una lista y el siguiente al entrar. */
    const val StaggerMs = 45L

    /** Solo los primeros elementos de una lista entran escalonados; pasado este índice entran juntos. */
    const val MaxStaggered = 8

    /** Arranca rápido y se posa despacio, como un objeto que cae sobre una superficie. */
    val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/**
 * Si el usuario desactivó las animaciones en el sistema (Ajustes > Accesibilidad > Quitar animaciones), la escala de
 * animación vale 0. Los efectos decorativos (fondo que respira, confeti, brillos) deben apagarse en ese caso.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    // En la vista previa de Android Studio no hay ContentResolver real: se muestra con movimiento.
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
