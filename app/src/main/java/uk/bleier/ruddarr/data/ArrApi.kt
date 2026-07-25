package uk.bleier.ruddarr.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uk.bleier.ruddarr.domain.InstanceConfig
import uk.bleier.ruddarr.domain.InstanceMode
import uk.bleier.ruddarr.domain.ServiceMetadata
import uk.bleier.ruddarr.domain.ServiceType
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class ArrApi {
    suspend fun library(instance: InstanceConfig): List<JSONObject> = getArray(instance, instance.service.libraryPath)
    suspend fun lookup(instance: InstanceConfig, query: String): List<JSONObject> = getArray(instance, "${instance.service.libraryPath}/lookup", mapOf("term" to query), 30_000)
    suspend fun details(instance: InstanceConfig, id: Int): JSONObject = getObject(instance, "${instance.service.libraryPath}/$id")
    suspend fun episodes(instance: InstanceConfig, seriesId: Int): List<JSONObject> = getArray(instance, "episode", mapOf("seriesId" to seriesId.toString()), 30_000)
    suspend fun episodeHistory(instance: InstanceConfig, episodeId: Int): List<JSONObject> = getObject(
        instance,
        "history",
        mapOf("episodeId" to episodeId.toString(), "page" to "1", "pageSize" to "100"),
    ).optJSONArray("records").toObjects()
    suspend fun releases(instance: InstanceConfig, parameters: Map<String, String>): List<JSONObject> = getArray(instance, "release", parameters, 90_000)
    suspend fun calendar(instance: InstanceConfig, parameters: Map<String, String>): List<JSONObject> = getArray(instance, "calendar", parameters, 60_000)
    suspend fun queue(instance: InstanceConfig): List<JSONObject> = getObject(
        instance,
        "queue",
        mapOf("includeMovie" to "true", "includeSeries" to "true", "includeEpisode" to "true", "pageSize" to "100"),
    ).optJSONArray("records").toObjects()
    suspend fun history(instance: InstanceConfig): List<JSONObject> = getObject(instance, "history", mapOf("page" to "1", "pageSize" to "100")).optJSONArray("records").toObjects()
    suspend fun mediaHistory(instance: InstanceConfig, mediaId: Int): List<JSONObject> = getObject(
        instance,
        "history",
        mapOf(
            (if (instance.service == ServiceType.RADARR) "movieId" else "seriesId") to mediaId.toString(),
            "page" to "1",
            "pageSize" to "100",
        ),
    ).optJSONArray("records").toObjects()
    suspend fun importableFiles(instance: InstanceConfig, downloadId: String): List<JSONObject> = getArray(
        instance,
        "manualimport",
        mapOf("downloadId" to downloadId, "filterExistingFiles" to "false"),
        30_000,
    )

    suspend fun metadata(instance: InstanceConfig): ServiceMetadata {
        val status = getObject(instance, "system/status")
        val folders = getArray(instance, "rootfolder", timeout = 60_000)
        val profiles = getArray(instance, "qualityprofile")
        val tags = getArray(instance, "tag")
        val disk = getArray(instance, "diskspace")
        return ServiceMetadata(
            name = status.optString("instanceName", status.optString("appName")),
            version = status.optString("version"),
            rootFolders = folders.map { ServiceMetadata.Choice(it.optInt("id"), it.optString("path")) },
            qualityProfiles = profiles.map { ServiceMetadata.Choice(it.optInt("id"), it.optString("name")) },
            tags = tags.map { ServiceMetadata.Choice(it.optInt("id"), it.optString("label")) },
            diskSpace = disk.map {
                ServiceMetadata.DiskSpace(
                    it.optString("label", it.optString("path")),
                    it.optLong("freeSpace"),
                    it.optLong("totalSpace"),
                )
            },
        )
    }

    suspend fun add(instance: InstanceConfig, media: JSONObject): JSONObject = request(instance, "POST", instance.service.libraryPath, media)
    suspend fun replace(instance: InstanceConfig, media: JSONObject): JSONObject = request(instance, "PUT", "${instance.service.libraryPath}/${media.optInt("id")}", media)
    suspend fun updateEditor(instance: InstanceConfig, media: JSONObject, moveFiles: Boolean = false) {
        val body = JSONObject().apply {
            if (instance.service == ServiceType.RADARR) {
                put("movieIds", JSONArray().put(media.optInt("id")))
                put("minimumAvailability", media.optString("minimumAvailability"))
            } else {
                put("seriesIds", JSONArray().put(media.optInt("id")))
                put("monitorNewItems", media.optString("monitorNewItems", "none"))
                put("seriesType", media.optString("seriesType", "standard"))
                put("seasonFolder", media.optBoolean("seasonFolder", true))
            }
            put("monitored", media.optBoolean("monitored"))
            put("qualityProfileId", media.optInt("qualityProfileId"))
            put("rootFolderPath", media.optString("rootFolderPath"))
            put("tags", media.optJSONArray("tags") ?: JSONArray())
            put("applyTags", "replace")
            if (moveFiles) put("moveFiles", true)
        }
        request(instance, "PUT", "${instance.service.libraryPath}/editor", body)
    }

    suspend fun deleteMedia(instance: InstanceConfig, id: Int, deleteFiles: Boolean, blocklist: Boolean) {
        request(
            instance,
            "DELETE",
            "${instance.service.libraryPath}/$id",
            query = mapOf(
                "deleteFiles" to deleteFiles.toString(),
                (if (instance.service == ServiceType.RADARR) "addImportExclusion" else "addImportListExclusion") to blocklist.toString(),
            ),
        )
    }

    suspend fun monitorEpisodes(instance: InstanceConfig, ids: List<Int>, monitored: Boolean) = request(
        instance,
        "PUT",
        "episode/monitor",
        JSONObject().put("episodeIds", JSONArray(ids)).put("monitored", monitored),
    )
    suspend fun deleteFile(instance: InstanceConfig, id: Int) = request(instance, "DELETE", if (instance.service == ServiceType.RADARR) "moviefile/$id" else "episodefile/$id")
    suspend fun deleteEpisodeFiles(instance: InstanceConfig, ids: List<Int>) = request(instance, "DELETE", "episodefile/bulk", JSONObject().put("episodeFileIds", JSONArray(ids)))
    suspend fun command(instance: InstanceConfig, name: String, parameters: JSONObject = JSONObject()) = request(instance, "POST", "command", parameters.put("name", name))
    suspend fun downloadRelease(instance: InstanceConfig, payload: JSONObject) = request(instance, "POST", "release", payload, timeout = 30_000)
    suspend fun deleteQueue(instance: InstanceConfig, id: Int, remove: Boolean, blocklist: Boolean, retry: Boolean) = request(
        instance,
        "DELETE",
        "queue/$id",
        query = mapOf("removeFromClient" to remove.toString(), "blocklist" to blocklist.toString(), "skipRedownload" to (!retry).toString()),
    )
    suspend fun notifications(instance: InstanceConfig): List<JSONObject> = getArray(instance, "notification")
    suspend fun saveNotification(instance: InstanceConfig, value: JSONObject): JSONObject = request(
        instance,
        if (value.has("id")) "PUT" else "POST",
        if (value.has("id")) "notification/${value.optInt("id")}" else "notification",
        value,
    )
    suspend fun deleteNotification(instance: InstanceConfig, id: Int) = request(instance, "DELETE", "notification/$id")

    private suspend fun getArray(instance: InstanceConfig, path: String, query: Map<String, String> = emptyMap(), timeout: Int = 10_000): List<JSONObject> =
        JSONArray(requestRaw(instance, "GET", path, query = query, timeout = timeout)).toObjects()

    private suspend fun getObject(instance: InstanceConfig, path: String, query: Map<String, String> = emptyMap(), timeout: Int = 10_000): JSONObject =
        request(instance, "GET", path, query = query, timeout = timeout)

    private suspend fun request(
        instance: InstanceConfig,
        method: String,
        path: String,
        body: JSONObject? = null,
        query: Map<String, String> = emptyMap(),
        timeout: Int = 10_000,
    ): JSONObject = JSONObject(requestRaw(instance, method, path, body, query, timeout).ifBlank { "{}" })

    private suspend fun requestRaw(
        instance: InstanceConfig,
        method: String,
        path: String,
        body: JSONObject? = null,
        query: Map<String, String> = emptyMap(),
        timeout: Int = 10_000,
    ): String = withContext(Dispatchers.IO) {
        val candidates = if (method == "GET") instance.candidates else instance.candidates.take(1)
        require(candidates.isNotEmpty()) { "Enter an instance URL first." }
        var lastError: Exception? = null
        for (baseUrl in candidates) {
            try {
                val url = buildUrl(baseUrl, path, query)
                val effectiveTimeout = if (instance.mode == InstanceMode.SLOW) maxOf(timeout, 30_000) else timeout
                val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = effectiveTimeout
                    readTimeout = effectiveTimeout
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Api-Key", instance.apiKey)
                    instance.headers.filter { it.name.isNotBlank() }.forEach { setRequestProperty(it.name.trimEnd(':'), it.value) }
                    if (body != null) {
                        doOutput = true
                        outputStream.bufferedWriter().use { it.write(body.toString()) }
                    }
                }
                val code = connection.responseCode
                val response = (if (code in 200..399) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..399) {
                    val message = runCatching { JSONObject(response).optString("message") }.getOrDefault(response).take(240)
                    throw ArrApiException("${instance.service.apiLabel} returned $code${message.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
                }
                return@withContext response
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: ArrApiException("Request failed.")
    }

    private fun buildUrl(base: String, path: String, query: Map<String, String>): String {
        val encodedQuery = query.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "${base.trimEnd('/')}/api/v3/${path.trimStart('/')}" + if (encodedQuery.isBlank()) "" else "?$encodedQuery"
    }
}

class ArrApiException(message: String) : Exception(message)

private fun JSONArray?.toObjects(): List<JSONObject> = this?.let { array ->
    List(array.length()) { index -> array.optJSONObject(index) ?: JSONObject() }
}.orEmpty()
