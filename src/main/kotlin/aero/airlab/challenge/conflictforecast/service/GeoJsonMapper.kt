package aero.airlab.challenge.conflictforecast.service

import aero.airlab.challenge.conflictforecast.api.Conflict
import aero.airlab.challenge.conflictforecast.api.ConflictForecastRequest
import aero.airlab.challenge.conflictforecast.api.SeparationRequirement
import aero.airlab.challenge.conflictforecast.geospatial.GeoPoint
import aero.airlab.challenge.conflictforecast.geospatial.TemporalGeoPoint

interface GeoJsonMapper {

    fun createConflictsAsFeatureCollections(conflictForecastRequest: ConflictForecastRequest): Map<String, Any>

    fun createFeatureCollection(conflictFeature: List<Conflict>, separationRequirements: List<SeparationRequirement>): Map<String, Any>

    fun createLineStringFeature(name: String, trajectoryId: Int, startPoint: TemporalGeoPoint, endPoint: TemporalGeoPoint?): Map<String, Any>
}