package una.codice.settings

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import una.codice.R

/**
 * El aviso de huella del sistema. Acepta la huella o el bloqueo del teléfono (PIN, patrón o contraseña) como
 * respaldo: quien no tiene huella, o el sensor falla con el dedo mojado, igual puede entrar.
 *
 * `BiometricPrompt` solo funciona sobre una [FragmentActivity]; por eso MainActivity ya no es una ComponentActivity.
 */
object BiometricGate {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** ¿Tiene el teléfono una huella registrada o un bloqueo de pantalla con el que verificar a la persona? */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Muestra el aviso. [onSuccess] solo corre si la verificación salió bien; [onFailure] corre cuando el aviso se
     * cierra sin éxito (la persona lo canceló, o se agotaron los intentos). Un dedo que no coincide no cierra el
     * aviso: el sistema deja reintentar.
     */
    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit = {}) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.app_biometric_prompt_subtitle))
            // Con DEVICE_CREDENTIAL no se puede poner un botón "Cancelar": el sistema ya trae el suyo.
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
