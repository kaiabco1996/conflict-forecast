package aero.airlab.challenge.conflictforecast.api

import aero.airlab.challenge.conflictforecast.geospatial.TemporalGeoPoint

/**
 * Describe a conflict between two trajectories.
 */
data class Conflict(
    /**
     * ID of first trajectory
     */
    val trajectoryA: Int,
    /**
     * Position and time of first trajectory when lateral separation breakdown starts.
     */
    val conflictStartA: TemporalGeoPoint,
    /**
     * Position and time of first trajectory when lateral separation breakdown ends.
     */
    val conflictEndA: TemporalGeoPoint?,
    /**
     * ID of second trajectory
     */
    val trajectoryB: Int,
    /**
     * Position and time of second trajectory when lateral separation breakdown starts.
     */
    val conflictStartB: TemporalGeoPoint,
    /**
     * Position and time of second trajectory when lateral separation breakdown ends.
     */
    val conflictEndB: TemporalGeoPoint?
)
