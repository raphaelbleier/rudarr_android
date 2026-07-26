package uk.bleier.ruddarr.domain

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class ServiceType(val apiLabel: String, val libraryPath: String) {
    RADARR("Radarr", "movie"),
    SONARR("Sonarr", "series"),
}

enum class InstanceMode { NORMAL, SLOW }

data class InstanceHeader(val name: String, val value: String)

data class InstanceConfig(
    val id: String = UUID.randomUUID().toString(),
    val service: ServiceType,
    val label: String,
    val primaryUrl: String,
    val alternateUrl: String = "",
    val apiKey: String,
    val headers: List<InstanceHeader> = emptyList(),
    val mode: InstanceMode = InstanceMode.NORMAL,
) {
    val candidates: List<String>
        get() = listOf(primaryUrl, alternateUrl)
            .map { it.withDefaultHttps().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()

    fun displayName() = label.ifBlank { service.apiLabel }

    fun validationError(): String? = when {
        primaryUrl.isBlank() -> "Enter an instance URL."
        !primaryUrl.withDefaultHttps().isHttpUrl() -> "Enter a valid instance host or URL."
        alternateUrl.isNotBlank() && !alternateUrl.withDefaultHttps().isHttpUrl() -> "Enter a valid alternate host or URL."
        apiKey.isBlank() -> "Enter an API key."
        headers.any { it.name.isBlank() } -> "Custom headers need a name."
        else -> null
    }

    fun toJson() = JSONObject().apply {
        put("id", id)
        put("service", service.name)
        put("label", label)
        put("primaryUrl", primaryUrl)
        put("alternateUrl", alternateUrl)
        put("apiKey", apiKey)
        put("mode", mode.name)
        put("headers", JSONArray().apply {
            headers.forEach { header -> put(JSONObject().put("name", header.name).put("value", header.value)) }
        })
    }

    companion object {
        fun fromJson(value: JSONObject) = InstanceConfig(
            id = value.optString("id").ifBlank { UUID.randomUUID().toString() },
            service = value.optString("service", ServiceType.RADARR.name)
                .let { runCatching { ServiceType.valueOf(it) }.getOrDefault(ServiceType.RADARR) },
            label = value.optString("label"),
            primaryUrl = value.optString("primaryUrl"),
            alternateUrl = value.optString("alternateUrl"),
            apiKey = value.optString("apiKey"),
            headers = value.optJSONArray("headers")?.let { headers ->
                List(headers.length()) { index ->
                    headers.getJSONObject(index).let { InstanceHeader(it.optString("name"), it.optString("value")) }
                }
            }.orEmpty(),
            mode = value.optString("mode", InstanceMode.NORMAL.name)
                .let { runCatching { InstanceMode.valueOf(it) }.getOrDefault(InstanceMode.NORMAL) },
        )
    }
}

data class ServiceMetadata(
    val name: String = "",
    val version: String = "",
    val rootFolders: List<Choice> = emptyList(),
    val qualityProfiles: List<Choice> = emptyList(),
    val tags: List<Choice> = emptyList(),
    val diskSpace: List<DiskSpace> = emptyList(),
) {
    data class Choice(val id: Int, val label: String)
    data class DiskSpace(val label: String, val freeSpace: Long, val totalSpace: Long)
}

data class MediaRecord(
    val service: ServiceType,
    val raw: JSONObject,
    val instanceId: String? = null,
) {
    val id: Int get() = raw.optInt("id")
    val title: String
        get() {
            val itemTitle = raw.optString("title").ifBlank { "Untitled" }
            val seriesTitle = raw.optJSONObject("series")?.optString("title").orEmpty()
            return if (service == ServiceType.SONARR && seriesTitle.isNotBlank()) "$seriesTitle · $itemTitle" else itemTitle
        }
    val year: Int get() = raw.optInt("year")
    val overview: String get() = raw.optString("overview")
    val monitored: Boolean get() = raw.optBoolean("monitored", false)
    val hasFile: Boolean
        get() = if (service == ServiceType.RADARR) raw.optBoolean("hasFile", raw.has("movieFile"))
        else raw.optJSONObject("statistics")?.optDouble("percentOfEpisodes", 0.0)?.let { it >= 100.0 } == true
    val episodeFileCount: Int
        get() = raw.optJSONObject("statistics")
            ?.takeIf { it.has("episodeFileCount") }
            ?.optInt("episodeFileCount")
            ?: raw.optInt("episodeFileCount")
    val episodeCount: Int
        get() = raw.optJSONObject("statistics")
            ?.takeIf { it.has("episodeCount") }
            ?.optInt("episodeCount")
            ?: raw.optInt("episodeCount")
    val fileId: Int
        get() = raw.optInt("movieFileId").takeIf { it > 0 } ?: raw.optJSONObject("movieFile")?.optInt("id") ?: 0
    val filePath: String
        get() = raw.optJSONObject("movieFile")?.optString("path").orEmpty()
    val fileSize: Long
        get() = raw.optJSONObject("movieFile")?.optLong("size") ?: 0L
    val fileQuality: String
        get() = raw.optJSONObject("movieFile")?.optJSONObject("quality")?.optJSONObject("quality")?.optString("name").orEmpty()
    val status: String get() = raw.optString("status").replaceFirstChar { it.uppercase() }
    val genres: List<String> get() = raw.optJSONArray("genres").toStringList()
    private val poster: JSONObject?
        get() = raw.optJSONArray("images")?.let { images ->
            List(images.length()) { images.optJSONObject(it) }
                .firstOrNull { it?.optString("coverType") == "poster" }
        } ?: raw.optJSONObject("series")?.optJSONArray("images")?.let { images ->
            List(images.length()) { images.optJSONObject(it) }
                .firstOrNull { it?.optString("coverType") == "poster" }
        }

    fun posterUrl(instance: InstanceConfig?): String? {
        val image = poster ?: return null
        val localPath = image.optString("url").takeIf { it.isNotBlank() }
        val localUrl = when {
            localPath == null -> null
            localPath.startsWith("http://") || localPath.startsWith("https://") -> localPath
            else -> instance?.candidates?.firstOrNull()?.let { base -> "$base/${localPath.trimStart('/')}" }
        }
        return localUrl ?: image.optString("remoteUrl").takeIf { it.isNotBlank() }
    }
    val details: String
        get() = buildList {
            if (year > 0) add(year.toString())
            if (service == ServiceType.SONARR && episodeCount > 0) add("$episodeFileCount / $episodeCount episodes")
            raw.optInt("runtime").takeIf { it > 0 }?.let { add("${it} min") }
            raw.optString("network").takeIf { it.isNotBlank() }?.let(::add)
            raw.optString("studio").takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(" · ")
    val stateLabel: String
        get() = when {
            hasFile -> "Downloaded"
            monitored -> "Missing"
            else -> "Unmonitored"
        }
    val calendarDate: String
        get() = raw.optString("inCinemas", raw.optString("airDateUtc", raw.optString("airDate"))).toDisplayDate()
    val isPremiere: Boolean
        get() = service == ServiceType.SONARR && raw.optInt("episodeNumber") == 1
    val isSpecial: Boolean
        get() = service == ServiceType.SONARR && raw.optInt("seasonNumber") == 0
    val seasons: List<SeasonRecord>
        get() = raw.optJSONArray("seasons")?.let { seasons ->
            List(seasons.length()) { index -> seasons.optJSONObject(index) }
                .filterNotNull()
                .map(::SeasonRecord)
                .sortedByDescending { it.number }
        }.orEmpty()
}

data class SeasonRecord(val raw: JSONObject) {
    val number: Int get() = raw.optInt("seasonNumber")
    val label: String get() = if (number == 0) "Specials" else "Season $number"
    val monitored: Boolean get() = raw.optBoolean("monitored", false)
    val episodeFileCount: Int get() = raw.optJSONObject("statistics")?.optInt("episodeFileCount") ?: 0
    val totalEpisodeCount: Int get() = raw.optJSONObject("statistics")?.optInt("totalEpisodeCount") ?: 0
    val sizeOnDisk: Long get() = raw.optJSONObject("statistics")?.optLong("sizeOnDisk") ?: 0L
}

fun releaseDownloadPayload(
    record: MediaRecord,
    release: JSONObject,
    seasonNumber: Int? = null,
    episodeId: Int? = null,
): JSONObject = JSONObject().apply {
    put("guid", release.optString("guid"))
    put("indexerId", release.optInt("indexerId"))
    if (record.service == ServiceType.RADARR) {
        put("movieId", record.id)
    } else if (episodeId != null) {
        put("episodeId", episodeId)
    } else {
        require(seasonNumber != null) { "Choose a season or episode before sending a Sonarr release." }
        put("seriesId", record.id)
        put("seasonNumber", seasonNumber)
    }
}

data class EpisodeRecord(val raw: JSONObject) {
    val id: Int get() = raw.optInt("id")
    val seriesId: Int get() = raw.optInt("seriesId")
    val seasonNumber: Int get() = raw.optInt("seasonNumber")
    val episodeNumber: Int get() = raw.optInt("episodeNumber")
    val title: String get() = raw.optString("title").ifBlank { "Episode $episodeNumber" }
    val overview: String get() = raw.optString("overview")
    val monitored: Boolean get() = raw.optBoolean("monitored", false)
    val fileId: Int get() = raw.optInt("episodeFileId").takeIf { it > 0 } ?: raw.optJSONObject("episodeFile")?.optInt("id") ?: 0
    val hasFile: Boolean get() = fileId > 0
    val airDate: String get() = raw.optString("airDateUtc", raw.optString("airDate")).toDisplayDate()
    val code: String get() = "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
}

data class QueueItem(val raw: JSONObject, val instance: InstanceConfig) {
    val id: Int get() = raw.optInt("id")
    val title: String get() = raw.optJSONObject("movie")?.optString("title")
        ?.ifBlank { null }
        ?: raw.optJSONObject("series")?.optString("title")?.ifBlank { null }
        ?: raw.optString("title").ifBlank { "Queue item" }
    val status: String get() = raw.optString("trackedDownloadStatus").ifBlank { raw.optString("status", "Queued") }
    val progress: Int get() = raw.optLong("sizeleft").let { left ->
        val total = raw.optLong("size")
        if (total > 0) ((1.0 - left.toDouble() / total) * 100).toInt().coerceIn(0, 100) else 0
    }
    val size: Long get() = raw.optLong("size")
    val sizeLeft: Long get() = raw.optLong("sizeleft")
    val downloadId: String get() = raw.optString("downloadId")
    val downloadClient: String get() = raw.optString("downloadClient")
    val indexer: String get() = raw.optString("indexer")
    val protocol: String get() = raw.optString("protocol", "Unknown").replaceFirstChar { it.uppercase() }
    val timeLeft: String get() = raw.opt("timeleft")?.toString().takeUnless { it == "null" }.orEmpty()
    val added: String get() = raw.optString("added").toDisplayDate()
    val errorMessage: String get() = raw.optString("errorMessage")
    val isIssue: Boolean get() = raw.optString("trackedDownloadStatus") != "ok" && raw.optString("trackedDownloadStatus").isNotBlank() || raw.optString("status") == "warning"
    val statusMessages: List<StatusMessage>
        get() = raw.optJSONArray("statusMessages")?.let { messages ->
            List(messages.length()) { index ->
                messages.optJSONObject(index)?.let { message ->
                    StatusMessage(message.optString("title"), message.optJSONArray("messages").toStringList())
                }
            }.filterNotNull()
        }.orEmpty()
    val quality: String
        get() = raw.optJSONObject("quality")?.optJSONObject("quality")?.optString("name").orEmpty()
    val languages: List<String>
        get() = raw.optJSONArray("languages")?.let { languages ->
            List(languages.length()) { index -> languages.optJSONObject(index)?.optString("name", languages.optJSONObject(index)?.optString("name").orEmpty()).orEmpty() }
                .filter { it.isNotBlank() }
        }.orEmpty()
    val customFormats: List<String>
        get() = raw.optJSONArray("customFormats")?.let { formats ->
            List(formats.length()) { index -> formats.optJSONObject(index)?.optString("name", formats.optJSONObject(index)?.optString("label").orEmpty()).orEmpty() }
                .filter { it.isNotBlank() }
        }.orEmpty()
    val customFormatScore: Int? get() = raw.optInt("customFormatScore").takeIf { raw.has("customFormatScore") }
    val needsManualImport: Boolean
        get() = downloadId.isNotBlank() && raw.optString("trackedDownloadStatus") == "warning" &&
            raw.optString("trackedDownloadState") in setOf("importPending", "importBlocked")

    data class StatusMessage(val title: String, val messages: List<String>)
}

data class HistoryItem(val raw: JSONObject, val instance: InstanceConfig) {
    val title: String get() = raw.optJSONObject("movie")?.optString("title")
        ?: raw.optJSONObject("series")?.optString("title")
        ?: raw.optString("sourceTitle", "History event")
    val eventType: String get() = raw.optString("eventType", "event").replaceFirstChar { it.uppercase() }
    val date: String get() = raw.optString("date").toDisplayDate()
}

fun JSONArray?.toStringList(): List<String> = this?.let { array ->
    List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
}.orEmpty()

fun String.toDisplayDate(): String = runCatching {
    DateTimeFormatter.ofPattern("dd MMM yyyy")
        .format(Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalDate())
}.getOrElse { this.take(10) }

private fun String.isHttpUrl(): Boolean = runCatching {
    val uri = URI(this.trim())
    uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private fun String.withDefaultHttps(): String = trim().let { value ->
    if (value.isBlank() || value.contains("://")) value else "https://$value"
}

fun LocalDate.toApiDate(): String = atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

fun Long.formatBytes(): String = when {
    this < 1024L * 1024L -> "${this / 1024} KB"
    this < 1024L * 1024L * 1024L -> "%.1f MB".format(this / (1024.0 * 1024.0))
    else -> "%.1f GB".format(this / (1024.0 * 1024.0 * 1024.0))
}
