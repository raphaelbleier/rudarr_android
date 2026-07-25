package uk.bleier.ruddarr.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchInstanceTest {
    @Test
    fun acceptsLocalHttpArrServer() {
        val instance = WatchInstance(
            service = WatchService.RADARR,
            baseUrl = "http://192.168.1.25:7878",
            apiKey = "local-key",
        )

        assertNull(instance.validationError())
        assertTrue(instance.isConfigured)
    }

    @Test
    fun rejectsIncompleteInstance() {
        val instance = WatchInstance(
            service = WatchService.SONARR,
            baseUrl = "sonarr.local:8989",
            apiKey = "",
        )

        assertEquals("Use a complete http or https URL.", instance.validationError())
    }

    @Test
    fun reportsMissingApiKeyAfterUrlIsValid() {
        val instance = WatchInstance(
            service = WatchService.SONARR,
            baseUrl = "https://sonarr.local",
            apiKey = "",
        )

        assertEquals("Enter the API key.", instance.validationError())
    }
}
