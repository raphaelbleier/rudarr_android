package uk.bleier.ruddarr.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import uk.bleier.ruddarr.domain.InstanceConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.util.Base64
import org.json.JSONArray

class SecureInstanceStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("ruddarr.instances", Context.MODE_PRIVATE)

    fun load(): List<InstanceConfig> = runCatching {
        val encoded = preferences.getString(INSTANCES_KEY, null) ?: return emptyList()
        val plaintext = decrypt(encoded)
        val values = JSONArray(plaintext)
        List(values.length()) { index -> InstanceConfig.fromJson(values.getJSONObject(index)) }
    }.getOrDefault(emptyList())

    fun save(instances: List<InstanceConfig>) {
        val json = JSONArray().apply { instances.forEach { put(it.toJson()) } }.toString()
        preferences.edit().putString(INSTANCES_KEY, encrypt(json)).apply()
    }

    fun loadDebugSeeds(): List<InstanceConfig> = runCatching {
        val values = context.assets.open(DEBUG_SEED_FILE).bufferedReader().use { reader -> JSONArray(reader.readText()) }
        List(values.length()) { index -> InstanceConfig.fromJson(values.getJSONObject(index)) }
            .onEach { instance -> require(instance.validationError() == null) { "Debug seed ${instance.displayName()} is invalid." } }
    }.getOrElse { error ->
        throw IllegalStateException("Add a valid $DEBUG_SEED_FILE debug asset first.", error)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_LENGTH) { "Stored instance data is invalid." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, payload.copyOfRange(0, IV_LENGTH)))
        }
        return cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val INSTANCES_KEY = "instances.v1"
        const val KEY_ALIAS = "ruddarr.instances.v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val DEBUG_SEED_FILE = "seed-instances.json"
    }
}
