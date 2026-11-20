package una.codice.core.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra el JWT antes de guardarlo en disco. Si alguien llega a copiar el archivo de preferencias (un respaldo, un
 * teléfono con root), solo ve texto cifrado: la llave vive en el Keystore de Android y no sale del dispositivo.
 */
interface TokenCipher {
    fun encrypt(plain: String): String

    /** El texto original, o `null` si no se puede descifrar (llave perdida o dato que no es nuestro): se pide iniciar sesión. */
    fun decrypt(encoded: String): String?

    companion object {
        /** Sin cifrado: solo para los tests de JVM, que no tienen Keystore. */
        val None: TokenCipher = object : TokenCipher {
            override fun encrypt(plain: String): String = plain
            override fun decrypt(encoded: String): String? = encoded
        }
    }
}

/** AES-256-GCM con una llave del `AndroidKeyStore`. El IV (12 bytes) viaja delante del texto cifrado, en Base64. */
@Singleton
class KeystoreTokenCipher @Inject constructor() : TokenCipher {

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? = try {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES)) }
        String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
    } catch (error: GeneralSecurityException) {
        null
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "codice_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
