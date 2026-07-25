package uk.bleier.ruddarr

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONObject
import uk.bleier.ruddarr.data.ArrApi
import uk.bleier.ruddarr.data.SecureInstanceStore
import uk.bleier.ruddarr.domain.HistoryItem
import uk.bleier.ruddarr.domain.InstanceConfig
import uk.bleier.ruddarr.domain.MediaRecord
import uk.bleier.ruddarr.domain.EpisodeRecord
import uk.bleier.ruddarr.domain.QueueItem
import uk.bleier.ruddarr.domain.ServiceMetadata
import uk.bleier.ruddarr.domain.ServiceType
import uk.bleier.ruddarr.domain.toApiDate
import uk.bleier.ruddarr.domain.releaseDownloadPayload
import java.time.LocalDate

enum class AppDestination { MOVIES, SERIES, CALENDAR, ACTIVITY, SETTINGS }

data class ReleaseContext(
    val record: MediaRecord,
    val seasonNumber: Int? = null,
    val episodeId: Int? = null,
)

data class AppState(
    val instances: List<InstanceConfig> = emptyList(),
    val activeRadarrId: String? = null,
    val activeSonarrId: String? = null,
    val destination: AppDestination = AppDestination.MOVIES,
    val movies: List<MediaRecord> = emptyList(),
    val series: List<MediaRecord> = emptyList(),
    val movieSearch: List<MediaRecord> = emptyList(),
    val seriesSearch: List<MediaRecord> = emptyList(),
    val calendar: List<MediaRecord> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val importFor: QueueItem? = null,
    val importFiles: List<JSONObject> = emptyList(),
    val metadata: Map<String, ServiceMetadata> = emptyMap(),
    val selected: MediaRecord? = null,
    val mediaHistory: List<HistoryItem> = emptyList(),
    val mediaHistoryForId: Int? = null,
    val episodes: List<EpisodeRecord> = emptyList(),
    val episodeHistory: List<HistoryItem> = emptyList(),
    val episodeHistoryForId: Int? = null,
    val releases: List<JSONObject> = emptyList(),
    val releaseContext: ReleaseContext? = null,
    val notifications: List<JSONObject> = emptyList(),
    val notificationFor: InstanceConfig? = null,
    val debugDemo: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
)

class RuddarrViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureInstanceStore(application)
    private val api = ArrApi()

    var state by mutableStateOf(AppState())
        private set

    init {
        val instances = store.load()
        state = state.copy(
            instances = instances,
            activeRadarrId = instances.firstOrNull { it.service == ServiceType.RADARR }?.id,
            activeSonarrId = instances.firstOrNull { it.service == ServiceType.SONARR }?.id,
        )
        refreshForDestination()
    }

    fun setDestination(destination: AppDestination) {
        state = state.copy(destination = destination, selected = null, message = null)
        refreshForDestination()
    }

    fun active(service: ServiceType): InstanceConfig? = state.instances.firstOrNull {
        it.id == if (service == ServiceType.RADARR) state.activeRadarrId else state.activeSonarrId
    }

    fun instanceFor(record: MediaRecord): InstanceConfig? = record.instanceId
        ?.let { id -> state.instances.firstOrNull { it.id == id } }
        ?: active(record.service)

    fun selectInstance(instanceId: String) {
        val instance = state.instances.firstOrNull { it.id == instanceId } ?: return
        state = if (instance.service == ServiceType.RADARR) state.copy(activeRadarrId = instance.id) else state.copy(activeSonarrId = instance.id)
        refreshForDestination()
    }

    fun saveInstance(instance: InstanceConfig) = launch("Saved ${instance.displayName()}.") {
        require(instance.validationError() == null) { instance.validationError().orEmpty() }
        val instances = state.instances.toMutableList().apply {
            val index = indexOfFirst { it.id == instance.id }
            if (index >= 0) set(index, instance) else add(instance)
        }
        store.save(instances)
        state = state.copy(
            instances = instances,
            activeRadarrId = if (instance.service == ServiceType.RADARR) instance.id else state.activeRadarrId,
            activeSonarrId = if (instance.service == ServiceType.SONARR) instance.id else state.activeSonarrId,
        )
        refreshMetadata(instance)
    }

    fun deleteInstance(instance: InstanceConfig) {
        val instances = state.instances.filterNot { it.id == instance.id }
        store.save(instances)
        state = state.copy(
            instances = instances,
            activeRadarrId = state.activeRadarrId.takeUnless { it == instance.id } ?: instances.firstOrNull { it.service == ServiceType.RADARR }?.id,
            activeSonarrId = state.activeSonarrId.takeUnless { it == instance.id } ?: instances.firstOrNull { it.service == ServiceType.SONARR }?.id,
            metadata = state.metadata - instance.id,
        )
        refreshForDestination()
    }

    fun seedDebugInstances() = launch("Loaded debug instances.") {
        val seeds = store.loadDebugSeeds()
        val instances = state.instances.toMutableList()
        seeds.forEach { seed ->
            val index = instances.indexOfFirst { existing ->
                existing.service == seed.service && (
                    existing.primaryUrl == seed.primaryUrl ||
                        (seed.label.isNotBlank() && existing.label == seed.label)
                    )
            }
            if (index >= 0) instances[index] = seed.copy(id = instances[index].id) else instances += seed
        }
        store.save(instances)
        state = state.copy(
            instances = instances,
            activeRadarrId = instances.firstOrNull { it.service == ServiceType.RADARR }?.id,
            activeSonarrId = instances.firstOrNull { it.service == ServiceType.SONARR }?.id,
        )
        refreshForDestination()
    }

    fun refreshForDestination() {
        if (state.debugDemo) return
        when (state.destination) {
        AppDestination.MOVIES -> refreshLibrary(ServiceType.RADARR)
        AppDestination.SERIES -> refreshLibrary(ServiceType.SONARR)
        AppDestination.CALENDAR -> refreshCalendar()
        AppDestination.ACTIVITY -> refreshActivity()
        AppDestination.SETTINGS -> refreshMetadataForAll()
        }
    }

    fun loadDebugDemo() {
        if (BuildConfig.DEBUG) state = DebugDemo.state(state.destination)
    }

    fun refreshLibrary(service: ServiceType) = launch {
        val instance = active(service) ?: return@launch show("Add a ${service.apiLabel} instance in Settings first.")
        val records = api.library(instance).map { MediaRecord(service, it, instance.id) }
        state = if (service == ServiceType.RADARR) state.copy(movies = records) else state.copy(series = records)
    }

    fun search(service: ServiceType, query: String) = launch {
        if (query.trim().length < 2) return@launch
        val instance = active(service) ?: return@launch show("Add a ${service.apiLabel} instance first.")
        val results = api.lookup(instance, query.trim()).map { MediaRecord(service, it, instance.id) }
        state = if (service == ServiceType.RADARR) state.copy(movieSearch = results) else state.copy(seriesSearch = results)
    }

    fun clearSearch(service: ServiceType) {
        state = if (service == ServiceType.RADARR) state.copy(movieSearch = emptyList()) else state.copy(seriesSearch = emptyList())
    }

    fun open(record: MediaRecord) = launch {
        if (state.debugDemo) {
            val id = if (record.service == ServiceType.SONARR && record.raw.optInt("seriesId") > 0) record.raw.optInt("seriesId") else record.id
            val selected = if (record.service == ServiceType.SONARR) state.series.firstOrNull { it.id == id } ?: record else state.movies.firstOrNull { it.id == id } ?: record
            state = state.copy(selected = selected, episodes = if (selected.service == ServiceType.SONARR) DebugDemo.episodesFor(selected.id) else emptyList(), mediaHistory = emptyList(), mediaHistoryForId = null, episodeHistory = emptyList(), episodeHistoryForId = null, releases = emptyList(), releaseContext = null)
            return@launch
        }
        val instance = instanceFor(record) ?: return@launch
        val id = if (record.service == ServiceType.SONARR && record.raw.optInt("seriesId") > 0) record.raw.optInt("seriesId") else record.id
        val selected = MediaRecord(record.service, api.details(instance, id), instance.id)
        val episodes = if (record.service == ServiceType.SONARR) api.episodes(instance, id).map(::EpisodeRecord) else emptyList()
        state = state.copy(selected = selected, mediaHistory = emptyList(), mediaHistoryForId = null, episodes = episodes, episodeHistory = emptyList(), episodeHistoryForId = null, releases = emptyList(), releaseContext = null)
    }

    fun preview(record: MediaRecord) {
        state = state.copy(selected = record, mediaHistory = emptyList(), mediaHistoryForId = null, episodes = emptyList(), episodeHistory = emptyList(), episodeHistoryForId = null, releases = emptyList(), releaseContext = null)
    }

    fun closeDetails() {
        state = state.copy(selected = null, mediaHistory = emptyList(), mediaHistoryForId = null, episodes = emptyList(), episodeHistory = emptyList(), episodeHistoryForId = null, releases = emptyList(), releaseContext = null)
    }

    fun lookupReleases(record: MediaRecord, seasonNumber: Int? = null, episodeId: Int? = null) = launch {
        val instance = instanceFor(record) ?: return@launch
        val query = buildMap {
            if (record.service == ServiceType.RADARR) put("movieId", record.id.toString())
            else if (episodeId != null) put("episodeId", episodeId.toString())
            else {
                require(seasonNumber != null) { "Choose a season before searching Sonarr releases." }
                put("seriesId", record.id.toString())
                put("seasonNumber", seasonNumber.toString())
            }
        }
        state = state.copy(
            releases = api.releases(instance, query),
            releaseContext = ReleaseContext(record, seasonNumber, episodeId),
        )
    }

    fun searchMedia(record: MediaRecord) = launch("Automatic search queued for ${record.title}.") {
        val instance = instanceFor(record) ?: return@launch
        val payload = if (record.service == ServiceType.RADARR) {
            JSONObject().put("movieIds", org.json.JSONArray().put(record.id))
        } else {
            JSONObject().put("seriesId", record.id)
        }
        api.command(instance, if (record.service == ServiceType.RADARR) "MoviesSearch" else "SeriesSearch", payload)
    }

    fun searchSeason(record: MediaRecord, seasonNumber: Int) = launch("Automatic search queued for Season $seasonNumber.") {
        require(record.service == ServiceType.SONARR) { "Season search is only available for Sonarr." }
        val instance = instanceFor(record) ?: return@launch
        api.command(instance, "SeasonSearch", JSONObject().put("seriesId", record.id).put("seasonNumber", seasonNumber))
    }

    fun searchEpisode(series: MediaRecord, episode: EpisodeRecord) = launch("Automatic search queued for ${episode.code}.") {
        val instance = instanceFor(series) ?: return@launch
        api.command(instance, "EpisodeSearch", JSONObject().put("episodeIds", org.json.JSONArray().put(episode.id)))
    }

    fun add(
        record: MediaRecord,
        rootFolder: String,
        profileId: Int,
        tagIds: Set<Int>,
        monitor: String,
        minimumAvailability: String? = null,
        seriesType: String? = null,
        seasonFolder: Boolean = true,
    ) = launch("Added ${record.title}.") {
        val instance = instanceFor(record) ?: return@launch
        val body = JSONObject(record.raw.toString()).apply {
            put("rootFolderPath", rootFolder)
            put("qualityProfileId", profileId)
            put("tags", org.json.JSONArray(tagIds.toList()))
            put("monitored", monitor != "none")
            put("addOptions", JSONObject().put("monitor", monitor))
            if (record.service == ServiceType.RADARR) {
                put("minimumAvailability", minimumAvailability ?: "announced")
            } else {
                put("seriesType", seriesType ?: "standard")
                put("seasonFolder", seasonFolder)
            }
        }
        api.add(instance, body)
        refreshLibrary(record.service)
    }

    fun setMonitored(record: MediaRecord, monitored: Boolean) = launch("Updated ${record.title}.") {
        val instance = instanceFor(record) ?: return@launch
        val body = JSONObject(record.raw.toString()).put("monitored", monitored)
        api.updateEditor(instance, body)
        state = state.copy(selected = MediaRecord(record.service, body, instance.id))
        refreshLibrary(record.service)
    }

    fun saveMediaEdits(
        record: MediaRecord,
        rootFolder: String,
        profileId: Int,
        tagIds: Set<Int>,
        minimumAvailability: String? = null,
        monitorNewItems: String? = null,
        seriesType: String? = null,
        seasonFolder: Boolean? = null,
        moveFiles: Boolean = false,
    ) = launch("Saved changes to ${record.title}.") {
        val instance = instanceFor(record) ?: return@launch
        val body = JSONObject(record.raw.toString()).apply {
            put("rootFolderPath", rootFolder)
            put("qualityProfileId", profileId)
            put("tags", org.json.JSONArray(tagIds.toList()))
            minimumAvailability?.let { put("minimumAvailability", it) }
            monitorNewItems?.let { put("monitorNewItems", it) }
            seriesType?.let { put("seriesType", it) }
            seasonFolder?.let { put("seasonFolder", it) }
        }
        api.updateEditor(instance, body, moveFiles)
        state = state.copy(selected = MediaRecord(record.service, body, instance.id))
        refreshLibrary(record.service)
    }

    fun setEpisodeMonitored(episode: EpisodeRecord, monitored: Boolean) = launch("Updated ${episode.code}.") {
        val instance = state.selected?.let(::instanceFor) ?: active(ServiceType.SONARR) ?: return@launch
        api.monitorEpisodes(instance, listOf(episode.id), monitored)
        state = state.copy(episodes = state.episodes.map {
            if (it.id == episode.id) EpisodeRecord(JSONObject(it.raw.toString()).put("monitored", monitored)) else it
        })
    }

    fun setSeasonMonitored(record: MediaRecord, seasonNumber: Int, monitored: Boolean) = launch("Updated Season $seasonNumber.") {
        require(record.service == ServiceType.SONARR) { "Season monitoring is only available for Sonarr." }
        val instance = instanceFor(record) ?: return@launch
        val existingSeasons = record.raw.optJSONArray("seasons") ?: throw IllegalStateException("This series has no season data.")
        val body = JSONObject(record.raw.toString()).apply {
            put("seasons", org.json.JSONArray().apply {
                repeat(existingSeasons.length()) { index ->
                    val season = JSONObject(existingSeasons.optJSONObject(index)?.toString() ?: "{}")
                    if (season.optInt("seasonNumber") == seasonNumber) season.put("monitored", monitored)
                    put(season)
                }
            })
        }
        api.updateEditor(instance, body)
        val refreshedEpisodes = api.episodes(instance, record.id).map(::EpisodeRecord)
        state = state.copy(selected = MediaRecord(ServiceType.SONARR, body, instance.id), episodes = refreshedEpisodes)
        refreshLibrary(ServiceType.SONARR)
    }

    fun loadEpisodeHistory(episode: EpisodeRecord) = launch {
        val instance = state.selected?.let(::instanceFor) ?: active(ServiceType.SONARR) ?: return@launch
        state = state.copy(
            episodeHistory = api.episodeHistory(instance, episode.id).map { HistoryItem(it, instance) },
            episodeHistoryForId = episode.id,
        )
    }

    fun loadMediaHistory(record: MediaRecord) = launch {
        val instance = instanceFor(record) ?: return@launch
        state = state.copy(
            mediaHistory = api.mediaHistory(instance, record.id).map { HistoryItem(it, instance) },
            mediaHistoryForId = record.id,
        )
    }

    fun deleteMovieFile(record: MediaRecord) = launch("Deleted the file for ${record.title}.") {
        require(record.service == ServiceType.RADARR && record.fileId > 0) { "This movie has no file to delete." }
        val instance = instanceFor(record) ?: return@launch
        api.deleteFile(instance, record.fileId)
        val updated = JSONObject(record.raw.toString()).apply {
            remove("movieFile")
            put("movieFileId", 0)
            put("hasFile", false)
        }
        state = state.copy(selected = MediaRecord(ServiceType.RADARR, updated, instance.id))
        refreshLibrary(ServiceType.RADARR)
    }

    fun deleteEpisodeFile(episode: EpisodeRecord) = launch("Deleted the file for ${episode.code}.") {
        require(episode.fileId > 0) { "This episode has no file to delete." }
        val instance = state.selected?.let(::instanceFor) ?: active(ServiceType.SONARR) ?: return@launch
        api.deleteFile(instance, episode.fileId)
        state = state.copy(episodes = state.episodes.map {
            if (it.id == episode.id) EpisodeRecord(JSONObject(it.raw.toString()).apply {
                remove("episodeFile")
                put("episodeFileId", 0)
            }) else it
        })
    }

    fun deleteSeasonFiles(record: MediaRecord, seasonNumber: Int) = launch("Deleted files for Season $seasonNumber.") {
        require(record.service == ServiceType.SONARR) { "Season files are only available for Sonarr." }
        val instance = instanceFor(record) ?: return@launch
        val seasonEpisodes = state.episodes.filter { it.seasonNumber == seasonNumber && it.fileId > 0 }
        require(seasonEpisodes.isNotEmpty()) { "This season has no files to delete." }
        api.deleteEpisodeFiles(instance, seasonEpisodes.map(EpisodeRecord::fileId))
        api.monitorEpisodes(instance, seasonEpisodes.map(EpisodeRecord::id), false)
        val refreshed = MediaRecord(ServiceType.SONARR, api.details(instance, record.id), instance.id)
        state = state.copy(selected = refreshed, episodes = api.episodes(instance, record.id).map(::EpisodeRecord))
        refreshLibrary(ServiceType.SONARR)
    }

    fun delete(record: MediaRecord, deleteFiles: Boolean, blocklist: Boolean) = launch("Deleted ${record.title}.") {
        val instance = instanceFor(record) ?: return@launch
        api.deleteMedia(instance, record.id, deleteFiles, blocklist)
        closeDetails()
        refreshLibrary(record.service)
    }

    fun download(record: MediaRecord, release: JSONObject) = launch("Release sent to the download client.") {
        val instance = instanceFor(record) ?: return@launch
        val context = state.releaseContext?.takeIf { it.record.id == record.id && it.record.instanceId == record.instanceId }
        val payload = releaseDownloadPayload(record, release, context?.seasonNumber, context?.episodeId)
        api.downloadRelease(instance, payload)
    }

    fun refreshCalendar(days: Int = 45) = launch {
        val start = LocalDate.now()
        val end = start.plusDays(days.toLong())
        val calendarQuery = mapOf("unmonitored" to "true", "start" to start.toApiDate(), "end" to end.toApiDate())
        val movies = kotlinx.coroutines.coroutineScope {
            state.instances.filter { it.service == ServiceType.RADARR }.map { instance ->
                async { api.calendar(instance, calendarQuery).map { json -> MediaRecord(ServiceType.RADARR, json, instance.id) } }
            }.awaitAll().flatten()
        }
        val episodes = kotlinx.coroutines.coroutineScope {
            state.instances.filter { it.service == ServiceType.SONARR }.map { instance ->
                async {
                    api.calendar(instance, calendarQuery + ("includeSeries" to "true"))
                        .map { json -> MediaRecord(ServiceType.SONARR, json, instance.id) }
                }
            }.awaitAll().flatten()
        }
        state = state.copy(calendar = (movies + episodes).sortedBy { it.raw.optString("inCinemas", it.raw.optString("airDateUtc")) })
    }

    fun refreshActivity() = launch {
        val instances = state.instances
        val queue = kotlinx.coroutines.coroutineScope {
            instances.map { instance -> async { api.queue(instance).map { QueueItem(it, instance) } } }.awaitAll().flatten()
        }
        val history = kotlinx.coroutines.coroutineScope {
            instances.map { instance -> async { api.history(instance).map { HistoryItem(it, instance) } } }.awaitAll().flatten()
        }
        state = state.copy(queue = queue, history = history)
    }

    fun refreshDownloads() = launch("Download clients refreshed.") {
        kotlinx.coroutines.coroutineScope { state.instances.map { async { api.command(it, "RefreshMonitoredDownloads") } }.awaitAll() }
        refreshActivity()
    }

    fun removeQueue(item: QueueItem, remove: Boolean, blocklist: Boolean, retry: Boolean) = launch("Queue task removed.") {
        api.deleteQueue(item.instance, item.id, remove, blocklist, retry)
        refreshActivity()
    }

    fun loadManualImport(item: QueueItem) = launch {
        require(item.downloadId.isNotBlank()) { "This queue task has no download ID." }
        state = state.copy(importFor = item, importFiles = api.importableFiles(item.instance, item.downloadId))
    }

    fun closeManualImport() {
        state = state.copy(importFor = null, importFiles = emptyList())
    }

    fun importFiles(item: QueueItem, files: List<JSONObject>) = launch("Manual import queued.") {
        require(files.isNotEmpty()) { "Select at least one file to import." }
        val payload = JSONObject().apply {
            put("importMode", "auto")
            put("files", org.json.JSONArray().apply {
                files.forEach { raw ->
                    put(JSONObject().apply {
                        put("path", raw.optString("path"))
                        put("downloadId", raw.optString("downloadId", item.downloadId))
                        copyIfPresent(raw, "quality")
                        copyIfPresent(raw, "languages")
                        copyIfPresent(raw, "releaseGroup")
                        if (item.instance.service == ServiceType.RADARR) {
                            raw.optJSONObject("movie")?.optInt("id")?.takeIf { it > 0 }?.let { put("movieId", it) }
                        } else {
                            raw.optJSONObject("series")?.optInt("id")?.takeIf { it > 0 }?.let { put("seriesId", it) }
                            raw.optJSONArray("episodes")?.let { episodes ->
                                put("episodeIds", org.json.JSONArray().apply {
                                    repeat(episodes.length()) { index -> episodes.optJSONObject(index)?.optInt("id")?.takeIf { it > 0 }?.let(::put) }
                                })
                            }
                            copyIfPresent(raw, "releaseType")
                        }
                    })
                }
            })
        }
        api.command(item.instance, "ManualImport", payload)
        closeManualImport()
        refreshActivity()
    }

    fun refreshMetadataForAll() = state.instances.forEach(::refreshMetadata)

    fun refreshMetadata(instance: InstanceConfig) = launch {
        val metadata = api.metadata(instance)
        state = state.copy(metadata = state.metadata + (instance.id to metadata))
    }

    fun loadNotifications(instance: InstanceConfig) = launch {
        state = state.copy(notificationFor = instance, notifications = api.notifications(instance))
    }

    fun closeNotifications() {
        state = state.copy(notificationFor = null, notifications = emptyList())
    }

    fun saveNotification(instance: InstanceConfig, notification: JSONObject) = launch("Notification updated.") {
        val saved = api.saveNotification(instance, notification)
        state = state.copy(notifications = state.notifications.map {
            if (it.optInt("id") == saved.optInt("id")) saved else it
        })
    }

    fun deleteNotification(instance: InstanceConfig, notification: JSONObject) = launch("Notification deleted.") {
        api.deleteNotification(instance, notification.optInt("id"))
        state = state.copy(notifications = state.notifications.filterNot { it.optInt("id") == notification.optInt("id") })
    }

    fun sendCommand(instance: InstanceConfig, command: String) = launch("$command started.") {
        api.command(instance, command)
    }

    fun consumeMessage() { state.message?.let { state = state.copy(message = null) } }

    private fun launch(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            state = state.copy(isLoading = true, message = null)
            runCatching { block() }
                .onSuccess { success?.let(::show) }
                .onFailure { show(it.message ?: "Request failed.") }
            state = state.copy(isLoading = false)
        }
    }

    private fun show(message: String) { state = state.copy(message = message) }
}

private fun JSONObject.copyIfPresent(source: JSONObject, key: String) {
    if (source.has(key) && !source.isNull(key)) put(key, source.get(key))
}
