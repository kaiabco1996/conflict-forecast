package aero.airlab.challenge.conflictforecast.api

import aero.airlab.challenge.conflictforecast.geospatial.ITemporalGeoPoint

data class Waypoint(override val lon: Double, override val lat: Double,
                    /**
                     * ETO time in epoch milliseconds
                     */
                    override val timestamp: Long) : ITemporalGeoPoint

data class Trajectory(val id: Int, val waypoints: List<Waypoint>)
