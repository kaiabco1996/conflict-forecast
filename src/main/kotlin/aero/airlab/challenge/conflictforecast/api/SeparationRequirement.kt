package aero.airlab.challenge.conflictforecast.api

import aero.airlab.challenge.conflictforecast.geospatial.GeoPoint

/**
 * Specifies separation requirement for a circular region.
 */
data class SeparationRequirement(
    /**
     * Center of the circular region
     */
    val center: GeoPoint,
    /**
     * Radius of the circular region in metres, exclusive
     */
    val radius: Double,
    /**
     * Required minimum lateral separation in the region in metres
     */
    val lateralSeparation: Double
)