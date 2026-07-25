package uk.bleier.ruddarr.wear

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class WatchService(val apiLabel: String, val libraryPath: String) {
    RADARR("Radarr", "movie"),
    SONARR("Sonarr", "series"),
}

data class WatchInstance(
    val service: WatchService,
    val baseUrl: String,
    val apiKey: String,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    fun validationError(): String? = when {
        baseUrl.isBlank() -> "Enter the local server URL."
        runCatching { URI(baseUrl.trim()) }.getOrNull()?.let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } != true ->
            "Use a complete http or https URL."
        apiKey.isBlank() -> "Enter the API key."
        else -> null
    }

    fun toJson() = JSONObject().apply {
        put("service", service.name)
        put("baseUrl", baseUrl)
        put("apiKey", apiKey)
    }

    companion object {
        fun fromJson(value: JSONObject) = WatchInstance(
            service = value.optString("service", WatchService.RADARR.name)
                .let { runCatching { WatchService.valueOf(it) }.getOrDefault(WatchService.RADARR) },
            baseUrl = value.optString("baseUrl"),
            apiKey = value.optString("apiKey"),
        )
    }
}

data class WatchLibraryItem(
    val title: String,
    val subtitle: String,
    val available: Boolean,
)

data class WatchSnapshot(
    val service: WatchService,
    val title: String,
    val version: String,
    val total: Int,
    val wanted: Int,
    val queueCount: Int,
    val items: List<WatchLibraryItem>,
)

class WearInstanceStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("ruddarr.wear.instances", Context.MODE_PRIVATE)

    fun load(): List<WatchInstance> = runCatching {
        val encoded = preferences.getString(INSTANCES_KEY, null) ?: return emptyList()
        val values = JSONArray(decrypt(encoded))
        List(values.length()) { index -> WatchInstance.fromJson(values.getJSONObject(index)) }
    }.getOrDefault(emptyList())

    fun save(instances: List<WatchInstance>) {
        val json = JSONArray().apply { instances.forEach { put(it.toJson()) } }.toString()
        preferences.edit { putString(INSTANCES_KEY, encrypt(json)) }
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
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_LENGTH) { "Stored watch configuration is invalid." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, IV_LENGTH)))
        }
        return cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val INSTANCES_KEY = "instances.v1"
        const val KEY_ALIAS = "ruddarr.wear.instances.v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}

class WearArrApi {
    suspend fun snapshot(instance: WatchInstance): WatchSnapshot = withContext(Dispatchers.IO) {
        val status = requestObject(instance, "system/status")
        val library = requestArray(instance, instance.service.libraryPath)
        val queue = requestObject(instance, "queue", mapOf("pageSize" to "100")).optJSONArray("records") ?: JSONArray()
        val items = library.take(12).map { media ->
            val title = media.optString("title").ifBlank { "Untitled" }
            val year = media.optInt("year").takeIf { it > 0 }?.toString().orEmpty()
            val available = if (instance.service == WatchService.RADARR) {
                media.optBoolean("hasFile")
            } else {
                media.optInt("episodeFileCount") > 0
            }
            val detail = when (instance.service) {
                WatchService.RADARR -> listOf(year, if (available) "Ready" else "Wanted").filter { it.isNotBlank() }.joinToString(" · ")
                WatchService.SONARR -> {
                    val episodes = media.optInt("episodeFileCount")
                    listOf(year, "$episodes episodes").filter { it.isNotBlank() }.joinToString(" · ")
                }
            }
            WatchLibraryItem(title, detail, available)
        }
        val wanted = library.count { media ->
            if (instance.service == WatchService.RADARR) !media.optBoolean("hasFile") else media.optInt("episodeFileCount") == 0
        }
        WatchSnapshot(
            service = instance.service,
            title = status.optString("instanceName", instance.service.apiLabel).ifBlank { instance.service.apiLabel },
            version = status.optString("version"),
            total = library.size,
            wanted = wanted,
            queueCount = queue.length(),
            items = items,
        )
    }

    private fun requestArray(instance: WatchInstance, path: String): List<JSONObject> {
        val values = JSONArray(request(instance, path))
        return List(values.length()) { index -> values.optJSONObject(index) ?: JSONObject() }
    }

    private fun requestObject(instance: WatchInstance, path: String, query: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(request(instance, path, query).ifBlank { "{}" })

    private fun request(instance: WatchInstance, path: String, query: Map<String, String> = emptyMap()): String {
        val queryString = query.entries.joinToString("&") { (name, value) -> "$name=$value" }
        val endpoint = "${instance.baseUrl.trimEnd('/')}/api/v3/${path.trimStart('/')}" +
            queryString.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Api-Key", instance.apiKey)
            val code = connection.responseCode
            val response = (if (code in 200..399) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code in 200..399) {
                val detail = runCatching { JSONObject(response).optString("message") }.getOrDefault(response).take(120)
                "${instance.service.apiLabel} returned $code${detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}
