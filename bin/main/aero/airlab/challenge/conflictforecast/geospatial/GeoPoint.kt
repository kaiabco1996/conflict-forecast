package aero.airlab.challenge.conflictforecast.geospatial

import java.io.Serializable

interface IGeoPoint {
    /**
     * Longitude in degrees
     */
    val lon: Double

    /**
     * Latitude in degrees
     */
    val lat: Double
}

interface ITemporalGeoPoint : IGeoPoint {
    /**
     * Timestamp associated with IGeoPoint in epoch milliseconds
     */
    val timestamp: Long
}

data class GeoPoint(override val lon: Double, override val lat: Double) : Serializable, IGeoPoint {
    companion object { val ZERO = GeoPoint(0.0, 0.0) }

    constructor(pt: IGeoPoint) : this(pt.lon, pt.lat)
}

data class TemporalGeoPoint(override val lon: Double, override val lat: Double,
                            override val timestamp: Long) : Serializable, ITemporalGeoPoint {
    companion object { val ZERO = TemporalGeoPoint(0.0, 0.0, 0L) }

    constructor(pt: ITemporalGeoPoint) : this(pt.lon, pt.lat, pt.timestamp)
}
