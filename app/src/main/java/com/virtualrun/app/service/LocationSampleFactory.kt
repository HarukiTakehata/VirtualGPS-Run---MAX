package com.virtualrun.app.service

import android.location.Location
import android.os.Build
import android.os.Bundle
import com.virtualrun.app.algorithm.PositionResult
import kotlin.math.*
import kotlin.random.Random

/** One immutable, time-stamped observation shared by all output adapters. */
internal class LocationSampleFactory {
    private var horizontalAccuracyMeters = 2.4f
    private var altitudeMeters = 45.0
    private var verticalAccuracyMetersState = 3.5f
    private var speedAccuracyMetersPerSecondState = 0.24f
    private var bearingAccuracyDegreesState = 2.8f
    private var satelliteCount = 12
    private data class SensorMetadata(
        val horizontalAccuracyMeters: Float,
        val altitudeMeters: Double,
        val verticalAccuracyMeters: Float,
        val speedAccuracyMetersPerSecond: Float,
        val bearingAccuracyDegrees: Float,
        val satellites: Int
    )


    private var sampleTimeMs = 0L
    private var lastSatelliteChangeMs = 0L
    private var deltaSeconds = 0f

    fun create(result: PositionResult, wallMs: Long, elapsedNanos: Long): Location {
        val nextMs = elapsedNanos / 1_000_000L
        deltaSeconds = if (sampleTimeMs == 0L) 0f else ((nextMs - sampleTimeMs) / 1000f).coerceIn(0f, 5f)
        sampleTimeMs = nextMs
        val metadata = evolveSensorMetadata()
        return buildMockLocation("gps", result, metadata, metadata.horizontalAccuracyMeters, wallMs, elapsedNanos)
    }

    private fun buildMockLocation(
        provider: String,
        result: com.virtualrun.app.algorithm.PositionResult,
        metadata: SensorMetadata,
        providerAccuracy: Float,
        wallClockMillis: Long,
        elapsedRealtimeNanosValue: Long
    ): Location = Location(provider).apply {
        latitude = result.latitude
        longitude = result.longitude
        altitude = metadata.altitudeMeters
        speed = result.speed
        bearing = result.bearing
        accuracy = providerAccuracy
        time = wallClockMillis
        elapsedRealtimeNanos = elapsedRealtimeNanosValue

        extras = Bundle().apply {
            putInt("satellites", metadata.satellites)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = metadata.verticalAccuracyMeters
            bearingAccuracyDegrees = metadata.bearingAccuracyDegrees
            speedAccuracyMetersPerSecond = metadata.speedAccuracyMetersPerSecond
        }
    }

    /**
     * 真实 GNSS 精度和卫星可见数会缓慢变化，而不是每秒完全相同。
     * 各状态使用均值回归，避免逐点独立随机数造成不自然的闪烁。
     */
    private fun evolveSensorMetadata(): SensorMetadata {
        horizontalAccuracyMeters = evolveValue(horizontalAccuracyMeters, 2.6f, 0.10f, 0.14f, 1.6f, 5.5f)
        altitudeMeters = evolveValue(altitudeMeters.toFloat(), 45.0f, 0.025f, 0.16f, 40.0f, 50.0f).toDouble()
        verticalAccuracyMetersState = evolveValue(verticalAccuracyMetersState, 4.0f, 0.08f, 0.20f, 2.5f, 7.0f)
        speedAccuracyMetersPerSecondState = evolveValue(
            speedAccuracyMetersPerSecondState,
            0.28f,
            0.12f,
            0.018f,
            0.12f,
            0.55f
        )
        bearingAccuracyDegreesState = evolveValue(bearingAccuracyDegreesState, 3.4f, 0.08f, 0.22f, 1.5f, 8.0f)

        if (sampleTimeMs - lastSatelliteChangeMs >= 5000L) {
            lastSatelliteChangeMs = sampleTimeMs
            satelliteCount = (satelliteCount + Random.nextInt(-1, 2)).coerceIn(8, 16)
        }

        return SensorMetadata(
            horizontalAccuracyMeters = horizontalAccuracyMeters,
            altitudeMeters = altitudeMeters,
            verticalAccuracyMeters = verticalAccuracyMetersState,
            speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecondState,
            bearingAccuracyDegrees = bearingAccuracyDegreesState,
            satellites = satelliteCount
        )
    }

    private fun evolveValue(
        current: Float,
        target: Float,
        meanReversion: Float,
        noiseScale: Float,
        minimum: Float,
        maximum: Float
    ): Float {
        val retention = (1.0 - meanReversion.toDouble()).pow(deltaSeconds.toDouble())
        val noiseFactor = sqrt((1.0 - retention * retention) / (1.0 - (1.0 - meanReversion) * (1.0 - meanReversion)))
        val next = current + (target - current) * (1.0 - retention).toFloat() + randomGaussian().toFloat() * noiseScale * noiseFactor.toFloat()
        return next.coerceIn(minimum, maximum)
    }

    private fun randomGaussian(): Double {
        val first = Random.nextDouble().coerceAtLeast(1e-12)
        val second = Random.nextDouble()
        return sqrt(-2.0 * ln(first)) * cos(2.0 * Math.PI * second)
    }


}
