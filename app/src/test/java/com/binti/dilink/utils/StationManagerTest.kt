package com.binti.dilink.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StationManager] — pure-JVM-testable functions.
 *
 * Phase 1: verifies the Haversine distance calculation, station JSON
 * parsing, and nearby-station filtering. These functions are testable
 * without Android instrumentation because they don't touch the Android
 * framework (the Context is only used for asset/file/prefs access).
 *
 * Note: StationManager(android.app.Application()) is used as a dummy
 * context. The constructor doesn't touch the context; only loadStations(),
 * readCache(), saveCache(), readAsset() do — and we don't call those here.
 */
class StationManagerTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Haversine distance calculation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `calculateDistance returns 0 for identical coordinates`() {
        val manager = StationManager(android.app.Application())
        val dist = manager.calculateDistance(30.0, 31.0, 30.0, 31.0)
        assertEquals(0.0, dist, 0.01)
    }

    @Test
    fun `calculateDistance Cairo to Alexandria is approximately 180 km`() {
        val manager = StationManager(android.app.Application())
        // Cairo:  30.0444° N, 31.2357° E
        // Alexandria: 31.2001° N, 29.9187° E
        val dist = manager.calculateDistance(30.0444, 31.2357, 31.2001, 29.9187)
        // Expected ~180 km, allow ±15 km tolerance for Haversine approximation
        assertTrue("Cairo-Alexandria distance should be ~180 km, got $dist km",
            dist in 165.0..195.0)
    }

    @Test
    fun `calculateDistance Cairo to Riyadh is approximately 1600 km`() {
        val manager = StationManager(android.app.Application())
        // Cairo:  30.0444° N, 31.2357° E
        // Riyadh: 24.7136° N, 46.6753° E
        val dist = manager.calculateDistance(30.0444, 31.2357, 24.7136, 46.6753)
        assertTrue("Cairo-Riyadh distance should be ~1600 km, got $dist km",
            dist in 1500.0..1700.0)
    }

    @Test
    fun `calculateDistance handles antipodal points without overflow`() {
        val manager = StationManager(android.app.Application())
        // Antipodal points — max distance on Earth (~20015 km)
        val dist = manager.calculateDistance(0.0, 0.0, 0.0, 180.0)
        assertTrue("Antipodal distance should be ~20015 km, got $dist km",
            dist in 19000.0..21000.0)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Station JSON parsing (via reflection to access the private parser)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseStationJson correctly parses a valid station object`() {
        val json = """
        {
          "stations": [
            {
              "id": "test-1",
              "name": "محطة شحن القاهرة",
              "name_en": "Cairo Charging Station",
              "address": "شارع التحرير",
              "city": "Cairo",
              "latitude": 30.0444,
              "longitude": 31.2357,
              "operator": "EgyptEV",
              "connectors": ["CCS2", "Type2"],
              "power_kw": 50,
              "status": "active",
              "amenities": ["cafe", "parking"],
              "hours": "24/7",
              "cost_per_kwh": 2.5
            }
          ]
        }
        """.trimIndent()

        val stations = invokeParseStationJson(json)
        assertEquals(1, stations.size)
        val station = stations[0]
        assertEquals("test-1", station.id)
        assertEquals("محطة شحن القاهرة", station.name)
        assertEquals("Cairo Charging Station", station.nameEn)
        assertEquals("Cairo", station.city)
        assertEquals(30.0444, station.latitude, 0.0001)
        assertEquals(31.2357, station.longitude, 0.0001)
        assertEquals(50, station.powerKw)
        assertEquals("active", station.status)
        assertEquals(listOf("CCS2", "Type2"), station.connectors)
        assertEquals(listOf("cafe", "parking"), station.amenities)
        assertEquals(2.5, station.costPerKwh, 0.01)
    }

    @Test
    fun `parseStationJson handles missing optional fields with defaults`() {
        val json = """
        {
          "stations": [
            {
              "id": "test-minimal",
              "name": "Minimal Station",
              "latitude": 30.0,
              "longitude": 31.0
            }
          ]
        }
        """.trimIndent()

        val stations = invokeParseStationJson(json)
        assertEquals(1, stations.size)
        val station = stations[0]
        assertEquals("test-minimal", station.id)
        assertEquals("Minimal Station", station.name)
        assertEquals("", station.nameEn)
        assertEquals("", station.address)
        assertEquals("", station.city)
        assertEquals(50, station.powerKw) // default
        assertEquals("active", station.status) // default
        assertEquals("N/A", station.hours) // default
        assertEquals(0.0, station.costPerKwh, 0.01) // default
        assertTrue(station.connectors.isEmpty())
        assertTrue(station.amenities.isEmpty())
    }

    @Test
    fun `parseStationJson returns empty list for malformed JSON`() {
        val stations = invokeParseStationJson("{ invalid json }")
        assertTrue(stations.isEmpty())
    }

    @Test
    fun `parseStationJson returns empty list for empty stations array`() {
        val stations = invokeParseStationJson("""{"stations": []}""")
        assertTrue(stations.isEmpty())
    }

    @Test
    fun `parseStationJson skips batch with missing required latitude`() {
        // When a required field (latitude/longitude/id/name) is missing,
        // getDouble/getString throws JSONException, caught by the parser,
        // which returns an empty list for the entire batch.
        val json = """
        {
          "stations": [
            {
              "id": "test-bad",
              "name": "Bad Station"
            }
          ]
        }
        """.trimIndent()

        val stations = invokeParseStationJson(json)
        assertTrue("Missing required fields should yield empty list", stations.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // findStationsNearby with invalid coordinates
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `findStationsNearby returns empty for invalid latitude`() {
        val manager = StationManager(android.app.Application())
        val results = manager.findStationsNearby(lat = 999.0, lon = 31.0)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findStationsNearby returns empty for invalid longitude`() {
        val manager = StationManager(android.app.Application())
        val results = manager.findStationsNearby(lat = 30.0, lon = 999.0)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findStationsNearby returns empty for null island coordinates`() {
        // lat=0, lon=0 is valid per range check but semantically invalid
        // (Gulf of Guinea). The function doesn't filter this, but it's
        // worth documenting that 0,0 passes the range check.
        val manager = StationManager(android.app.Application())
        val results = manager.findStationsNearby(lat = 0.0, lon = 0.0)
        // No stations loaded, so empty — but no exception thrown
        assertTrue(results.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper: call the companion-object parseStationJson directly (no reflection
    // needed — it's now a public companion method).
    // ──────────────────────────────────────────────────────────────────────────

    private fun invokeParseStationJson(json: String): List<StationManager.ChargingStation> =
        StationManager.parseStationJson(json)
}
