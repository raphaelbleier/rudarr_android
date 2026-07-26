package uk.bleier.ruddarr.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelsTest {
    @Test
    fun instanceConfigurationRoundTripsEncryptedStorePayloadFields() {
        val source = InstanceConfig(
            id = "instance-id",
            service = ServiceType.SONARR,
            label = "Home Sonarr",
            primaryUrl = "https://sonarr.example.test/",
            alternateUrl = "https://sonarr.lan",
            apiKey = "secret",
            headers = listOf(InstanceHeader("X-Forwarded-Host", "ruddarr.example.test")),
            mode = InstanceMode.SLOW,
        )

        val restored = InstanceConfig.fromJson(source.toJson())

        assertEquals(source, restored)
        assertEquals(listOf("https://sonarr.example.test", "https://sonarr.lan"), restored.candidates)
    }

    @Test
    fun instanceValidationNormalizesHostsAndRejectsInvalidUrls() {
        val valid = InstanceConfig(service = ServiceType.RADARR, label = "", primaryUrl = "https://radarr.example.test", apiKey = "key")

        assertNull(valid.validationError())
        assertNull(valid.copy(primaryUrl = "radarr.example.test").validationError())
        assertEquals(listOf("https://radarr.example.test"), valid.copy(primaryUrl = "radarr.example.test").candidates)
        assertTrue(valid.copy(alternateUrl = "ftp://radarr.example.test").validationError()?.contains("valid") == true)
        assertTrue(valid.copy(apiKey = "").validationError()?.contains("API key") == true)
    }

    @Test
    fun corruptStoredServiceFallsBackToRadarrInsteadOfCrashing() {
        val restored = InstanceConfig.fromJson(JSONObject().apply {
            put("service", "NOT_A_SERVICE")
            put("primaryUrl", "https://radarr.example.test")
            put("apiKey", "key")
            put("mode", "UNKNOWN")
        })

        assertEquals(ServiceType.RADARR, restored.service)
        assertEquals(InstanceMode.NORMAL, restored.mode)
        assertFalse(restored.candidates.isEmpty())
    }

    @Test
    fun episodePresentationUsesSeasonCodeAndDownloadedState() {
        val episode = EpisodeRecord(JSONObject().apply {
            put("id", 99)
            put("seriesId", 12)
            put("seasonNumber", 2)
            put("episodeNumber", 3)
            put("title", "The Test")
            put("episodeFile", JSONObject().put("id", 7))
        })

        assertEquals("S02E03", episode.code)
        assertTrue(episode.hasFile)
        assertEquals(7, episode.fileId)
        assertEquals("The Test", episode.title)

        val idOnlyEpisode = EpisodeRecord(JSONObject().put("episodeFileId", 8))
        assertTrue(idOnlyEpisode.hasFile)
        assertEquals(8, idOnlyEpisode.fileId)
    }

    @Test
    fun queueItemExposesLocalQueueDetailsAndIssueState() {
        val instance = InstanceConfig(
            service = ServiceType.RADARR,
            label = "Home Radarr",
            primaryUrl = "https://radarr.example.test",
            apiKey = "key",
        )
        val item = QueueItem(JSONObject().apply {
            put("id", 42)
            put("movie", JSONObject().put("title", "The Test Movie"))
            put("size", 1_000L)
            put("sizeleft", 250L)
            put("protocol", "usenet")
            put("downloadClient", "SABnzbd")
            put("downloadId", "SABnzbd_test-download")
            put("trackedDownloadStatus", "warning")
            put("trackedDownloadState", "importBlocked")
            put("statusMessages", org.json.JSONArray().put(JSONObject().put("title", "Import").put("messages", org.json.JSONArray().put("Path is unavailable"))))
            put("quality", JSONObject().put("quality", JSONObject().put("name", "HD-1080p")))
        }, instance)

        assertEquals("The Test Movie", item.title)
        assertEquals(75, item.progress)
        assertEquals("Usenet", item.protocol)
        assertEquals("SABnzbd", item.downloadClient)
        assertEquals("HD-1080p", item.quality)
        assertTrue(item.needsManualImport)
        assertTrue(item.isIssue)
        assertEquals(listOf("Path is unavailable"), item.statusMessages.single().messages)
    }

    @Test
    fun queueItemHidesNullTimeLeftValues() {
        val instance = InstanceConfig(
            service = ServiceType.RADARR,
            label = "Home Radarr",
            primaryUrl = "https://radarr.example.test",
            apiKey = "key",
        )
        val jsonNull = QueueItem(
            JSONObject().put("timeleft", JSONObject.NULL),
            instance,
        )
        val missing = QueueItem(
            JSONObject(),
            instance,
        )

        assertEquals("", jsonNull.timeLeft)
        assertEquals("", missing.timeLeft)
    }

    @Test
    fun movieRecordExposesLocalMovieFileMetadata() {
        val movie = MediaRecord(ServiceType.RADARR, JSONObject().apply {
            put("id", 12)
            put("title", "The Test Movie")
            put("hasFile", true)
            put("movieFile", JSONObject().apply {
                put("id", 34)
                put("path", "/movies/The Test Movie.mkv")
                put("size", 1_024L)
                put("quality", JSONObject().put("quality", JSONObject().put("name", "HD-1080p")))
            })
        })

        assertTrue(movie.hasFile)
        assertEquals(34, movie.fileId)
        assertEquals("/movies/The Test Movie.mkv", movie.filePath)
        assertEquals(1_024L, movie.fileSize)
        assertEquals("HD-1080p", movie.fileQuality)
    }

    @Test
    fun calendarRecordExposesEpisodeFilters() {
        val episode = MediaRecord(ServiceType.SONARR, JSONObject().apply {
            put("id", 56)
            put("series", JSONObject().put("title", "The Test Series"))
            put("title", "Pilot")
            put("airDate", "2026-09-01")
            put("seasonNumber", 0)
            put("episodeNumber", 1)
            put("monitored", true)
        })

        assertEquals("2026-09-01", episode.calendarDate)
        assertTrue(episode.isPremiere)
        assertTrue(episode.isSpecial)
        assertTrue(episode.monitored)
    }

    @Test
    fun seriesRecordExposesSeasonMonitoringAndStatistics() {
        val series = MediaRecord(ServiceType.SONARR, JSONObject().apply {
            put("seasons", org.json.JSONArray().put(JSONObject().apply {
                put("seasonNumber", 2)
                put("monitored", true)
                put("statistics", JSONObject().apply {
                    put("episodeFileCount", 8)
                    put("totalEpisodeCount", 10)
                    put("sizeOnDisk", 2_048L)
                })
            }))
        })

        val season = series.seasons.single()

        assertEquals("Season 2", season.label)
        assertTrue(season.monitored)
        assertEquals(8, season.episodeFileCount)
        assertEquals(10, season.totalEpisodeCount)
        assertEquals(2_048L, season.sizeOnDisk)
    }

    @Test
    fun seriesRecordUsesNestedEpisodeStatisticsInItsSummary() {
        val series = MediaRecord(ServiceType.SONARR, JSONObject().apply {
            put("year", 2024)
            put("statistics", JSONObject().apply {
                put("episodeFileCount", 12)
                put("episodeCount", 24)
            })
        })

        assertEquals(12, series.episodeFileCount)
        assertEquals(24, series.episodeCount)
        assertEquals("2024 · 12 / 24 episodes", series.details)
    }

    @Test
    fun seriesRecordFallsBackToLegacyEpisodeCounts() {
        val series = MediaRecord(ServiceType.SONARR, JSONObject().apply {
            put("episodeFileCount", 3)
            put("episodeCount", 5)
        })

        assertEquals(3, series.episodeFileCount)
        assertEquals(5, series.episodeCount)
    }

    @Test
    fun releaseDownloadPayloadTargetsMovieSeasonOrEpisode() {
        val release = JSONObject().put("guid", "release-guid").put("indexerId", 17)
        val movie = MediaRecord(ServiceType.RADARR, JSONObject().put("id", 10))
        val series = MediaRecord(ServiceType.SONARR, JSONObject().put("id", 20))

        val moviePayload = releaseDownloadPayload(movie, release)
        val seasonPayload = releaseDownloadPayload(series, release, seasonNumber = 3)
        val episodePayload = releaseDownloadPayload(series, release, episodeId = 44)

        assertEquals(10, moviePayload.optInt("movieId"))
        assertEquals(20, seasonPayload.optInt("seriesId"))
        assertEquals(3, seasonPayload.optInt("seasonNumber"))
        assertEquals(44, episodePayload.optInt("episodeId"))
        assertEquals("release-guid", episodePayload.optString("guid"))
    }
}
