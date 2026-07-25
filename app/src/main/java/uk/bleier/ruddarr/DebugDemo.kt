package uk.bleier.ruddarr

import org.json.JSONArray
import org.json.JSONObject
import uk.bleier.ruddarr.domain.EpisodeRecord
import uk.bleier.ruddarr.domain.HistoryItem
import uk.bleier.ruddarr.domain.InstanceConfig
import uk.bleier.ruddarr.domain.MediaRecord
import uk.bleier.ruddarr.domain.QueueItem
import uk.bleier.ruddarr.domain.ServiceMetadata
import uk.bleier.ruddarr.domain.ServiceType

internal object DebugDemo {
    private val radarr = InstanceConfig(
        id = "demo-radarr",
        service = ServiceType.RADARR,
        label = "Home Radarr",
        primaryUrl = "https://radarr.demo.local",
        apiKey = "debug-only",
    )
    private val sonarr = InstanceConfig(
        id = "demo-sonarr",
        service = ServiceType.SONARR,
        label = "Home Sonarr",
        primaryUrl = "https://sonarr.demo.local",
        apiKey = "debug-only",
    )

    private val movies = listOf(
        movie(1, "Afterglow", 2026, monitored = true, hasFile = false, genres = listOf("Drama", "Science Fiction")),
        movie(2, "Northern Lights", 2025, monitored = true, hasFile = true, genres = listOf("Adventure", "Family")),
        movie(3, "The Last Archive", 2024, monitored = false, hasFile = false, genres = listOf("Mystery")),
    )
    private val series = listOf(series(101, "Signal / Noise"))
    private val episodes = listOf(
        episode(1001, 101, 2, 1, "The Broadcast", true, true, "2026-07-27"),
        episode(1002, 101, 2, 2, "Dead Air", true, false, "2026-08-03"),
        episode(1003, 101, 2, 3, "The Relay", true, false, "2026-08-10"),
        episode(1004, 101, 1, 8, "The Signal", true, true, "2025-10-14"),
    )

    fun state(destination: AppDestination): AppState {
        val movieRecords = movies.map { MediaRecord(ServiceType.RADARR, it, radarr.id) }
        val seriesRecords = series.map { MediaRecord(ServiceType.SONARR, it, sonarr.id) }
        val calendar = listOf(
            MediaRecord(ServiceType.RADARR, JSONObject(movies[0].toString()).put("inCinemas", "2026-08-01"), radarr.id),
            MediaRecord(ServiceType.SONARR, calendarEpisode(episodes[0]), sonarr.id),
            MediaRecord(ServiceType.SONARR, calendarEpisode(episodes[1]), sonarr.id),
        )
        val queue = listOf(
            QueueItem(JSONObject().apply {
                put("id", 71)
                put("movie", movies[0])
                put("size", 5_600_000_000L)
                put("sizeleft", 1_500_000_000L)
                put("protocol", "usenet")
                put("downloadClient", "SABnzbd")
                put("indexer", "Demo Indexer")
                put("trackedDownloadStatus", "ok")
                put("trackedDownloadState", "downloading")
                put("quality", quality("HD-1080p", 1080))
                put("languages", array(language("English")))
                put("added", "2026-07-25T12:30:00Z")
            }, radarr),
            QueueItem(JSONObject().apply {
                put("id", 72)
                put("series", series[0])
                put("size", 2_400_000_000L)
                put("sizeleft", 2_400_000_000L)
                put("protocol", "torrent")
                put("downloadClient", "qBittorrent")
                put("trackedDownloadStatus", "warning")
                put("trackedDownloadState", "importBlocked")
                put("errorMessage", "The local demo marks this import as blocked.")
                put("statusMessages", array(JSONObject().put("title", "Import").put("messages", array("Destination is unavailable"))))
                put("quality", quality("WEBDL-1080p", 1080))
            }, sonarr),
        )
        val history = listOf(
            HistoryItem(JSONObject().put("id", 1).put("eventType", "downloadFolderImported").put("movie", movies[1]).put("date", "2026-07-24T20:10:00Z"), radarr),
            HistoryItem(JSONObject().put("id", 2).put("eventType", "grabbed").put("series", series[0]).put("date", "2026-07-24T18:50:00Z"), sonarr),
        )
        val metadata = mapOf(
            radarr.id to ServiceMetadata(
                name = "Radarr", version = "5.0 demo",
                rootFolders = listOf(ServiceMetadata.Choice(1, "/media/movies")),
                qualityProfiles = listOf(ServiceMetadata.Choice(1, "HD-1080p")),
                tags = listOf(ServiceMetadata.Choice(1, "favourites")),
                diskSpace = listOf(ServiceMetadata.DiskSpace("/media", 840_000_000_000L, 2_000_000_000_000L)),
            ),
            sonarr.id to ServiceMetadata(
                name = "Sonarr", version = "4.0 demo",
                rootFolders = listOf(ServiceMetadata.Choice(1, "/media/tv")),
                qualityProfiles = listOf(ServiceMetadata.Choice(1, "WEBDL-1080p")),
                tags = listOf(ServiceMetadata.Choice(1, "weekly")),
                diskSpace = listOf(ServiceMetadata.DiskSpace("/media", 840_000_000_000L, 2_000_000_000_000L)),
            ),
        )
        return AppState(
            instances = listOf(radarr, sonarr),
            activeRadarrId = radarr.id,
            activeSonarrId = sonarr.id,
            destination = destination,
            movies = movieRecords,
            series = seriesRecords,
            calendar = calendar,
            queue = queue,
            history = history,
            metadata = metadata,
            debugDemo = true,
        )
    }

    fun episodesFor(seriesId: Int): List<EpisodeRecord> = episodes.filter { it.optInt("seriesId") == seriesId }.map(::EpisodeRecord)

    private fun movie(id: Int, title: String, year: Int, monitored: Boolean, hasFile: Boolean, genres: List<String>) = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("year", year)
        put("overview", "A local debug record used only to verify Ruddarr for Android layouts and screenshots.")
        put("status", "released")
        put("monitored", monitored)
        put("hasFile", hasFile)
        put("genres", array(*genres.toTypedArray()))
        put("runtime", 112)
        put("studio", "Ruddarr Studios")
        put("qualityProfileId", 1)
        put("rootFolderPath", "/media/movies")
        put("minimumAvailability", "released")
        put("tags", array(1))
        put("inCinemas", "2026-08-01")
        put("ratings", JSONObject().put("imdb", JSONObject().put("value", 7.8)).put("tmdb", JSONObject().put("value", 8.1)))
        if (hasFile) put("movieFile", JSONObject().put("id", 502).put("path", "/media/movies/$title/$title.mkv").put("size", 4_600_000_000L).put("quality", quality("HD-1080p", 1080)))
    }

    private fun series(id: Int, title: String) = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("year", 2025)
        put("overview", "A thriller about a pirate radio signal that grows louder whenever nobody is listening.")
        put("status", "continuing")
        put("monitored", true)
        put("genres", array("Thriller", "Drama"))
        put("network", "Ruddarr+ One")
        put("qualityProfileId", 1)
        put("rootFolderPath", "/media/tv")
        put("seriesType", "standard")
        put("monitorNewItems", "all")
        put("seasonFolder", true)
        put("tags", array(1))
        put("seasons", array(
            season(2, true, 1, 3, 4_600_000_000L),
            season(1, true, 1, 8, 3_100_000_000L),
            season(0, false, 0, 2, 0L),
        ))
    }

    private fun season(number: Int, monitored: Boolean, files: Int, total: Int, size: Long) = JSONObject().apply {
        put("seasonNumber", number)
        put("monitored", monitored)
        put("statistics", JSONObject().put("episodeFileCount", files).put("totalEpisodeCount", total).put("sizeOnDisk", size))
    }

    private fun episode(id: Int, seriesId: Int, season: Int, number: Int, title: String, monitored: Boolean, hasFile: Boolean, airDate: String) = JSONObject().apply {
        put("id", id)
        put("seriesId", seriesId)
        put("seasonNumber", season)
        put("episodeNumber", number)
        put("title", title)
        put("overview", "Demo episode overview.")
        put("monitored", monitored)
        put("airDate", airDate)
        if (hasFile) put("episodeFile", JSONObject().put("id", id + 2000).put("quality", quality("WEBDL-1080p", 1080)))
    }

    private fun calendarEpisode(episode: JSONObject) = JSONObject(episode.toString()).apply {
        put("series", series[0])
        put("airDate", episode.optString("airDate"))
    }

    private fun quality(name: String, resolution: Int) = JSONObject().put("quality", JSONObject().put("name", name).put("resolution", resolution))
    private fun language(name: String) = JSONObject().put("name", name)
    private fun array(vararg values: Any) = JSONArray().apply { values.forEach(::put) }
}
