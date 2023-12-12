package aero.airlab.challenge.conflictforecast.service.impl

import aero.airlab.challenge.conflictforecast.api.Conflict
import aero.airlab.challenge.conflictforecast.api.ConflictForecastRequest
import aero.airlab.challenge.conflictforecast.api.ConflictForecastResponse
import aero.airlab.challenge.conflictforecast.api.SeparationRequirement
import aero.airlab.challenge.conflictforecast.geospatial.TemporalGeoPoint
import aero.airlab.challenge.conflictforecast.service.ConflictForecastService
import aero.airlab.challenge.conflictforecast.service.ConflictForecastServiceV2
import aero.airlab.challenge.conflictforecast.service.GeoJsonMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.geotools.referencing.GeodeticCalculator
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * This is a sample class representing a geojson transformers.
 * @property conflictForecastService conflict service class for v1.
 * @property conflictForecastServiceV2 conflict service class for v2.
 */
@Service
class GeoJsonMapperImpl(
    private val conflictForecastService: ConflictForecastService,
    private val conflictForecastServiceV2: ConflictForecastServiceV2

) : GeoJsonMapper {

    val objectMapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Identifies from the trajectories to get conflicts and transform them to GeoJson features - brute force
     * @param conflictForecastRequest The conflicts identified from the trajectories.
     * @return The GeoJson of features as feature collections.
     */
    override fun createConflictsAsFeatureCollections(conflictForecastRequest: ConflictForecastRequest): Map<String, Any> {
        val conflictForecastResponse: ConflictForecastResponse = conflictForecastService.createConflict(conflictForecastRequest)
        return createFeatureCollection(conflictForecastResponse.conflicts, conflictForecastRequest.separationRequirements)
    }

    /**
     * Identifies from the trajectories to get conflicts and transform them to GeoJson features - performant version
     * @param conflictForecastRequest The conflicts identified from the trajectories.
     * @return The GeoJson of features as feature collections.
     */
    override suspend fun createConflictsAsFeatureCollectionsV2(conflictForecastRequest: ConflictForecastRequest): Map<String, Any> {
        val conflictForecastResponse: ConflictForecastResponse = conflictForecastServiceV2.createConflict(conflictForecastRequest)
        return createFeatureCollection(conflictForecastResponse.conflicts, conflictForecastRequest.separationRequirements)
    }

    /**
     * Transform conflicts to GeoJson features
     * @param conflictFeature The list of conflicts identified from the trajectories.
     * @param separationRequirements The list of separation regions required.
     * @return The GeoJson of features as feature collections.
     */
    override fun createFeatureCollection(conflictFeature: List<Conflict>, separationRequirements: List<SeparationRequirement>): Map<String, Any> {
        val featureCollection = mutableMapOf<String, Any>()
        featureCollection["type"] = "FeatureCollection"

        val features = mutableListOf<Map<String, Any>>()
        for (conflict in conflictFeature) {
            val r = Random.nextInt(256)
            val g = Random.nextInt(256)
            val b = Random.nextInt(256)
            features.add(
                createLineStringFeature(
                    "Trajectory A",
                    conflict.trajectoryA,
                    conflict.conflictStartA,
                    conflict.conflictEndA,
                    r,
                    g,
                    b
                )
            )
            features.add(
                createLineStringFeature(
                    "Trajectory B",
                    conflict.trajectoryB,
                    conflict.conflictStartB,
                    conflict.conflictEndB,
                    r,
                    g,
                    b
                )
            )
        }
        for (region in createCircleRegionOfSeparation(separationRequirements)){
            println("region: $region")
            features.add(region)
        }
        featureCollection["features"] = features
        return featureCollection
    }

    /**
     * Create linestrings and their property details from conflicting trajectories
     * @param name Whether trajectory A or B.
     * @param trajectoryId I.d. of aircraft.
     * @param startPoint start of aircraft trajectory.
     * @param endPoint end of aircraft trajectory.
     * @param r red color of line stroke.
     * @param g green color of line stroke.
     * @param b blue color of line stroke.
     * @return The GeoJson of linestring
     */
    override fun createLineStringFeature(
        name: String,
        trajectoryId: Int,
        startPoint: TemporalGeoPoint,
        endPoint: TemporalGeoPoint?,
        r: Int,
        g: Int,
        b: Int
    ): Map<String, Any> {
        val feature = mutableMapOf<String, Any>()
        feature["type"] = "Feature"

        val geometry = mutableMapOf<String, Any>()
        geometry["type"] = "LineString"
        geometry["coordinates"] = listOf(
            listOf(startPoint.lon, startPoint.lat),
            listOf(endPoint?.lon, endPoint?.lat)
        )

        feature["geometry"] = geometry

        val properties = mutableMapOf<String, Any>()
        properties["name"] = name
        properties["trajectoryId"] = trajectoryId
        properties["startTime"] = startPoint.timestamp
        if (endPoint != null) {
            properties["endTime"] = endPoint.timestamp
        }
        properties["stroke"] = "rgb(%d, %d, %d)".format(r, g, b)
        properties["stroke-width"] = 2
        properties["stroke-opacity"] = 1

        feature["properties"] = properties

        return feature
    }

    /**
     * Create separation region polygons and their property details
     * @param separationRequirements Whether trajectory A or B.
     * @return The GeoJson of Circular polygon of separation region
     */
    fun createCircleRegionOfSeparation(separationRequirements: List<SeparationRequirement>): List<Map<String, Any>> {
        val features = separationRequirements.map { requirement ->
            mapOf(
                "type" to "Feature",
                "geometry" to mapOf(
                    "type" to "Polygon",
                    "coordinates" to listOf(
                        generateCircleCoordinates(
                            requirement.center.lon,
                            requirement.center.lat,
                            requirement.radius
                        )
                    )
                ),
                "properties" to mapOf(
                    "radius" to requirement.radius,
                    "lateralSeparation" to requirement.lateralSeparation
                )
            )
        }
        println("circle features: $features")
        return features
    }

    /**
     * Create coordinates for separation region polygon
     * @param lon longitude of center.
     * @param lat latitude of center.
     * @param radius radius of region.
     * @return The coordinates of lat and long of Circular polygon of separation region
     */
    private fun generateCircleCoordinates(lon: Double, lat: Double, radius: Double): List<List<Double>> {
        val numPoints = 60 // Adjust the number of points based on your requirements
        val coordinates = mutableListOf<List<Double>>()
        val geoCalc = GeodeticCalculator()

        repeat(numPoints + 1) { i ->
            var customHeading: Double = (360.0/numPoints) * (i+1)
            if (customHeading > 180) {customHeading = -180 + (customHeading-180)}
            // Set the starting point (longitude, latitude)
            println("azimuth: $customHeading")
            geoCalc.setStartingGeographicPoint(lon, lat)

            // Set your own heading angle in degrees (clockwise from North)
            geoCalc.setDirection(customHeading, radius)

            // Calculate the destination point (longitude, latitude)
            var destLon = geoCalc.destinationGeographicPoint.x
            var destLat = geoCalc.destinationGeographicPoint.y
            coordinates.add(listOf(destLon, destLat))

        }

        coordinates.add(coordinates[0]) // Close the polygon

        return coordinates
    }

}

