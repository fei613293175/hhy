package cc.orbexa.hhy.network

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Stores only the refresh credential required to restore a user session. Access tokens
 * deliberately stay in memory. Keystore invalidation is treated as a signed-out state.
 */
class AuthSessionStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(session: AuthSessionResource): Boolean = runCatching {
        val deviceId = session.device?.get("deviceId")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank) ?: return false
        val plainText = HhyNetworkJson.value.encodeToString(
            StoredAuthSession(session.refreshToken, deviceId),
        ).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(plainText)
        preferences.edit()
            .putString(CIPHERTEXT_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }.getOrElse {
        clear()
        false
    }

    fun load(): StoredAuthSession? = runCatching {
        val ciphertext = preferences.getString(CIPHERTEXT_KEY, null) ?: return null
        val iv = preferences.getString(IV_KEY, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
        }
        val decoded = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        HhyNetworkJson.value.decodeFromString<StoredAuthSession>(decoded)
    }.getOrElse {
        clear()
        null
    }

    /**
     * Make credential removal durable before any caller continues to the anonymous flow.
     * An asynchronous write could leave a refresh credential recoverable after logout or
     * cryptographic invalidation if the process is terminated immediately.
     */
    @SuppressLint("ApplySharedPref")
    fun clear() {
        preferences.edit().remove(CIPHERTEXT_KEY).remove(IV_KEY).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    @Serializable
    data class StoredAuthSession(
        val refreshToken: String,
        val deviceId: String,
    )

    private companion object {
        const val PREFERENCES_NAME = "hhy_auth_session"
        const val CIPHERTEXT_KEY = "refresh_ciphertext"
        const val IV_KEY = "refresh_iv"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hhy.auth.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
