package com.virtualrun.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.MapTileIndex

class ChinaMapTileSourceTest {
    @Test
    fun satelliteUsesSeparateCacheAndImageryEndpoint() {
        val road = ChinaMapTileSource()
        val satellite = ChinaMapTileSource(MapMode.SATELLITE)
        assertTrue(road.name() != satellite.name())
        val url = satellite.getTileURLString(MapTileIndex.getTileIndex(18, 215837, 99333))
        assertTrue(url.startsWith("https://webst"))
        assertTrue(url.contains("style=6"))
        assertTrue(url.contains("x=215837&y=99333&z=18"))
        assertEquals(18, satellite.maximumZoomLevel)
    }

    @Test
    fun roadRemainsDefaultAndModesRoundTrip() {
        assertTrue(ChinaMapTileSource().getTileURLString(MapTileIndex.getTileIndex(1, 0, 0)).contains("style=8"))
        assertEquals(MapMode.SATELLITE, MapMode.ROAD.next())
        assertEquals(MapMode.ROAD, MapMode.SATELLITE.next())
        assertEquals(MapMode.ROAD, MapMode.fromPreference(null))
        assertEquals(MapMode.ROAD, MapMode.fromPreference("invalid"))
        MapMode.entries.forEach { assertEquals(it, MapMode.fromPreference(it.name)) }
    }

    @Test
    fun rasterTileSourceStopsBeforeAutoNaviBlankPlaceholderLevels() {
        val source = ChinaMapTileSource()

        assertEquals(CHINA_MAP_MIN_ZOOM_LEVEL, source.minimumZoomLevel)
        assertEquals(CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL, source.maximumZoomLevel)
        assertEquals(22, CHINA_MAP_MAX_ZOOM_LEVEL)
        assertTrue(CHINA_MAP_MAX_ZOOM_LEVEL > CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL)

        val tileIndex = MapTileIndex.getTileIndex(CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL, 215_837, 99_333)
        val url = source.getTileURLString(tileIndex)
        assertTrue(url.contains("x=215837"))
        assertTrue(url.contains("y=99333"))
        assertTrue(url.contains("z=18"))
    }
}
