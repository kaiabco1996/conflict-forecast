package aero.airlab.challenge.conflictforecast.service.impl

import aero.airlab.challenge.conflictforecast.api.*
import aero.airlab.challenge.conflictforecast.geospatial.*
import aero.airlab.challenge.conflictforecast.service.ConflictForecastService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class ConflictForecastServiceImpl : ConflictForecastService {

    private val logger = KotlinLogging.logger {}

    /**
     * Create list of conficts based on separation region and aircraft trajectories
     * @param conflictForecastRequest request containing region and trajectories.
     * @return The list of conflicting trajectories
     */
    override fun createConflict(conflictForecastRequest: ConflictForecastRequest): ConflictForecastResponse {
        val geodeticCalc: GeodeticCalc = GeodeticCalc.geodeticCalcWSSS()
        // Get the earliest timestamp of the first waypoint of all trajectories
        var earliestTimestamp = conflictForecastRequest.trajectories
            .flatMap { trajectory -> trajectory.waypoints }
            .minByOrNull(Waypoint::timestamp)
            ?.timestamp
        var lastTimestamp = conflictForecastRequest.trajectories
            .flatMap { trajectory -> trajectory.waypoints }
            .maxByOrNull(Waypoint::timestamp)
            ?.timestamp

        logger.info { "First Timestamp to start payload process: $earliestTimestamp" }
        logger.info { "Final Timestamp to end payload process: $lastTimestamp" }
        val conflictsResponse = mutableListOf<Conflict>()
        if (earliestTimestamp != null && lastTimestamp != null) {
            earliestTimestamp += 5000
            lastTimestamp += 5000
            for (timestamp in earliestTimestamp until lastTimestamp step 5000) {

                logger.info { "$timestamp: Extracting relevant waypoint coordinates for current timestamp" }
                val geoPointPairMap = geoPointPairMutableMap(conflictForecastRequest, timestamp, geodeticCalc)

                logger.info { "$timestamp: Finding lateral distance between different aircrafts pairs" }
                val distancePairs = getAircraftDistancePairs(geoPointPairMap, geodeticCalc)

                logger.info { "$timestamp: Finding relevant regions to different aircrafts" }
                val geoPointRegionMap = evaluateAircraftRegions(geoPointPairMap, conflictForecastRequest, geodeticCalc)

                logger.info { "$timestamp: Finding conflicts from lateral distance given the relevant regions and interpolated position" }
                findConflictsFromSeparationRegions(distancePairs, geoPointRegionMap, geoPointPairMap, conflictsResponse)

                logger.info { "$timestamp: Number of relevant aircrafts in current time: ${geoPointPairMap.size}" }
                logger.info { "$timestamp: Number of aircraft combinations lateral distances in current time: ${distancePairs.size}" }
                logger.info { "$timestamp: Number of conflicts from regions in current time: ${conflictsResponse.size}" }

            }
        }

        return ConflictForecastResponse(conflictsResponse)
    }

    /**
     * Create list of conficts based on separation region and aircraft trajectories
     * @param distancePairs lateral separation distances for different pairs of trajectories.
     * @param geoPointRegionMap geopoints and their respective regions they fall under.
     * @param geoPointPairMap geopoints and their trajectories from previous step to current step.
     * @param conflictsResponse list of conflicts to aggregate.
     */
    private fun findConflictsFromSeparationRegions(
        distancePairs: MutableList<AircraftDistancePair>,
        geoPointRegionMap: MutableMap<Int, SeparationRequirement?>,
        geoPointPairMap: MutableMap<Int, GeoPointPair>,
        conflictsResponse: MutableList<Conflict>
    ) {
        for (aircrafts in distancePairs) { // O(trajectories choose 2 )
            if (geoPointRegionMap[aircrafts.id2] == null || geoPointRegionMap[aircrafts.id1] == null
                || geoPointPairMap[aircrafts.id2] == null || geoPointPairMap[aircrafts.id1] == null
            ) {
                continue
            } else {
                if (geoPointRegionMap[aircrafts.id1] == geoPointRegionMap[aircrafts.id2]
                    && geoPointRegionMap[aircrafts.id2]?.lateralSeparation!! > aircrafts.distance
                ) {
                    addConflictHandler(aircrafts, geoPointPairMap, conflictsResponse)

                } else if (geoPointRegionMap[aircrafts.id1] != geoPointRegionMap[aircrafts.id2]
                    && java.lang.Double.max(
                        geoPointRegionMap[aircrafts.id2]!!.lateralSeparation,
                        geoPointRegionMap[aircrafts.id1]!!.lateralSeparation
                    )
                    > aircrafts.distance
                ) {
                    addConflictHandler(aircrafts, geoPointPairMap, conflictsResponse)
                }
            }
            continue
        }
    }

    /**
     * evaluate the separation region based on aircraft trajectories at current time
     * @param geoPointPairMap geopoints and their trajectories.
     * @param conflictForecastRequest contains the separation regions for evaluation.
     * @param geodeticCalc supporting object for geodetic calculations.
     * @return The map of trajectories and their regions
     */
    private fun evaluateAircraftRegions(
        geoPointPairMap: MutableMap<Int, GeoPointPair>,
        conflictForecastRequest: ConflictForecastRequest,
        geodeticCalc: GeodeticCalc
    ): MutableMap<Int, SeparationRequirement?> {
        val geoPointRegionMap = mutableMapOf<Int, SeparationRequirement?>()
        for (trajectory in geoPointPairMap) { // O(trajectory within time * region)
            var minRegionDistance: Double = Double.POSITIVE_INFINITY
            var selectedRegion: SeparationRequirement? =
                null

            for (region in conflictForecastRequest.separationRequirements) {
                val distanceFromCenter: Double = geodeticCalc.distance(trajectory.value.geoPointCurrent, region.center)


                if (distanceFromCenter <= minRegionDistance
                    && distanceFromCenter < region.radius
                ) {
                    minRegionDistance = distanceFromCenter
                    selectedRegion = region
                }
            }

            geoPointRegionMap[trajectory.key] = selectedRegion
        }
        return geoPointRegionMap
    }

    /**
     * evaluate the combinations of aircraft trajectories and their lateral distances
     * @param geoPointPairMap geopoints and their trajectories.
     * @param geodeticCalc supporting object for geodetic calculations.
     * @return The map of trajectories and their regions
     */
    private fun getAircraftDistancePairs(
        geoPointPairMap: MutableMap<Int, GeoPointPair>,
        geodeticCalc: GeodeticCalc
    ): MutableList<AircraftDistancePair> {
        val distancePairs = mutableListOf<AircraftDistancePair>()
        for (key in geoPointPairMap.keys) { // O(key ** 2)
            for (key2 in geoPointPairMap.keys) {
                if (key != key2) {
                    // Check if the pair already exists
                    val existingPair =
                        distancePairs.find { it.id1 == key && it.id2 == key2 || it.id1 == key2 && it.id2 == key }
                    if (existingPair == null && geoPointPairMap.get(key) != null && geoPointPairMap.get(key2) != null) {
                        val lateralDistance: Double =
                            geodeticCalc.distance(
                                geoPointPairMap.get(key)!!.geoPointCurrent,
                                geoPointPairMap.get(key2)!!.geoPointCurrent
                            )
                        distancePairs.add(AircraftDistancePair(key, key2, lateralDistance))
                    }
                } else {
                    continue
                }
            }
        }
        return distancePairs
    }

    /**
     * evaluate the relevant trajectory for the timestamp given
     * @param conflictForecastRequest contains the trajectories to analyse.
     * @param geodeticCalc supporting object for geodetic calculations.
     * @param timestamp current timestamp to evaluate on.
     * @return The map of trajectory geopoints and their timestamps
     */
    private fun geoPointPairMutableMap(
        conflictForecastRequest: ConflictForecastRequest,
        timestamp: Long,
        geodeticCalc: GeodeticCalc
    ): MutableMap<Int, GeoPointPair> {
        val geoPointPairMap = mutableMapOf<Int, GeoPointPair>()
        for (trajectory in conflictForecastRequest.trajectories) {// O(trajectory)
            val waypointsBefore = trajectory.waypoints.filter { it.timestamp <= timestamp }
            val waypointsAfter = trajectory.waypoints.filter { it.timestamp > timestamp }
            if (waypointsBefore.isEmpty() || waypointsAfter.isEmpty()) {
                logger.info { "No relevant waypoints for $timestamp for ${trajectory.id}" }
                continue
            }
            //given the waypoints, find the two waypoints in between the current time,
            val (geoPointBefore: TemporalGeoPoint?, geoPointAfter: TemporalGeoPoint?) = findWaypointsAroundTimestamp(
                waypointsBefore,
                waypointsAfter,
                targetTimestamp = timestamp
            )
            //find the headingAndDistanceTo from the two waypoints and
            if (geoPointBefore != null && geoPointAfter != null) {
                val (heading, distance) = geodeticCalc.headingAndDistanceTo(geoPointBefore, geoPointAfter)
                //interpolate based on the timestamp differences for the distance at current timestamp
                val interpolatedDistance: Double =
                    interpolatePosition(geoPointBefore, geoPointAfter, timestamp, distance)
                //from the initial waypoint, heading & dist, find the next position using nextPointFrom
                val geoPointAtCurrentTime: TemporalGeoPoint = convertToTemporalGeoPoint(
                    geodeticCalc.nextPointFrom(geoPointBefore, heading, interpolatedDistance),
                    timestamp
                )
                val geoPointPair = GeoPointPair(geoPointBefore, geoPointAtCurrentTime)
                geoPointPairMap[trajectory.id] = geoPointPair

            } else {
                // Handle the case where either geoPointBefore or geoPointAfter is null
                logger.error { "One or both geo points are null for $timestamp for ${trajectory.id}" }
                throw IllegalArgumentException("One or both geo points are null.")
            }
        }
        return geoPointPairMap
    }

    /**
     * aggregate the conflicts
     * @param aircrafts contains the trajectories to analyse.
     * @param geoPointPairMap trajectory pairs of timestamp and their geopoints.
     * @param conflictsResponse aggregates the conflicts across.
     */
    private fun addConflictHandler(
        aircrafts: AircraftDistancePair,
        geoPointPairMap: MutableMap<Int, GeoPointPair>,
        conflictsResponse: MutableList<Conflict>
    ) {
        val newConflict = Conflict(
            aircrafts.id1,
            geoPointPairMap[aircrafts.id1]!!.geoPointCurrent,
            geoPointPairMap[aircrafts.id1]!!.geoPointBefore,
            geoPointPairMap[aircrafts.id1]!!.geoPointCurrent,
            aircrafts.id2,
            geoPointPairMap[aircrafts.id1]!!.geoPointCurrent,
            geoPointPairMap[aircrafts.id2]!!.geoPointBefore,
            geoPointPairMap[aircrafts.id2]!!.geoPointCurrent
        )
        val existingConflictIndex = conflictsResponse.indexOfFirst {
            it.trajectoryA == newConflict.trajectoryA &&
                    it.conflictBeforeA!!.timestamp == newConflict.conflictBeforeA!!.timestamp &&
                    it.conflictBeforeA!!.lat == newConflict.conflictBeforeA!!.lat &&
                    it.conflictBeforeA!!.lon == newConflict.conflictBeforeA!!.lon &&
                    it.conflictBeforeB!!.timestamp == newConflict.conflictBeforeB!!.timestamp &&
                    it.conflictBeforeB!!.lat == newConflict.conflictBeforeB!!.lat &&
                    it.conflictBeforeB!!.lon == newConflict.conflictBeforeB!!.lon &&
                    it.trajectoryB == newConflict.trajectoryB
        }

        if (existingConflictIndex != -1) {
            // Update the endTime of the existing conflict
            conflictsResponse[existingConflictIndex] = conflictsResponse[existingConflictIndex].copy(
                conflictEndA = newConflict.conflictEndA,
                conflictEndB = newConflict.conflictEndB
            )
        } else {
            // Add the conflict if it doesn't already exist
            conflictsResponse.add(newConflict)
        }
    }

    /**
     * Find the trajectories that correspond to the current timestamp
     * @param waypointsBefore filtered waypoints that are below or equal to this timestamp.
     * @param waypointsAfter filtered waypoints that are above this timestamp.
     * @param targetTimestamp current timestamp.
     * @return pair of geopoints that exist within the given timestamp
     */
    fun findWaypointsAroundTimestamp(waypointsBefore: List<Waypoint>, waypointsAfter: List<Waypoint>, targetTimestamp: Long): Pair<TemporalGeoPoint?, TemporalGeoPoint?> {

        val waypointBefore = waypointsBefore.maxByOrNull { it.timestamp }
        val waypointAfter = waypointsAfter.minByOrNull { it.timestamp }

        val geoPointBefore: TemporalGeoPoint? = waypointBefore?.toTemporalGeoPoint()
        val geoPointAfter: TemporalGeoPoint? = waypointAfter?.toTemporalGeoPoint()

        return Pair(geoPointBefore, geoPointAfter)
    }

    /**
     * convert waypoint to temporal geopoint
     */
    fun Waypoint.toTemporalGeoPoint(): TemporalGeoPoint {
        return TemporalGeoPoint(lon, lat, timestamp)
    }

    /**
     * Find the distance given the timestamps and distance to interpolate from
     * @param geoPoint1  waypoint that are below or equal to this timestamp.
     * @param geoPoint2  waypoint that are above the timestamp.
     * @param targetTimestamp current timestamp.
     * @param distance distance to interpolate.
     * @return interpolated distance
     */
    fun interpolatePosition(geoPoint1: TemporalGeoPoint, geoPoint2: TemporalGeoPoint, targetTimestamp: Long, distance: Double): Double {
        // Calculate the time difference between the two points
        val timeDifference = geoPoint2.timestamp - geoPoint1.timestamp

        // Calculate the interpolation factor based on the target timestamp
        val interpolationFactor = (targetTimestamp - geoPoint1.timestamp).toDouble() / timeDifference.toDouble()

        // Return the interpolated distance
        return distance * interpolationFactor
    }

    /**
     * create a geopoint with timestamp from geopoint and timestamp
     * @param geoPoint  geopoint with lat and long
     * @param timestamp  waypoint that are above the timestamp.
     * @return geopoint with timestamp
     */
    fun convertToTemporalGeoPoint(geoPoint: IGeoPoint, timestamp: Long): TemporalGeoPoint {
        return TemporalGeoPoint(geoPoint.lon, geoPoint.lat, timestamp)
    }

}