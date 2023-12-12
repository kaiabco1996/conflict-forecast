package aero.airlab.challenge.conflictforecast.service.impl

import aero.airlab.challenge.conflictforecast.api.*
import aero.airlab.challenge.conflictforecast.geospatial.*
import aero.airlab.challenge.conflictforecast.service.ConflictForecastService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class ConflictForecastServiceImpl : ConflictForecastService {

    private val logger = KotlinLogging.logger {}

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
        // Loop from the current timestamp to the end timestamp, increasing by 5 seconds each iteration
        if (earliestTimestamp != null && lastTimestamp != null) {
            earliestTimestamp += 5000
            lastTimestamp += 5000
            //init empty conflict array
            // O(trajectories choose 2) + O(trajectory within time * region) + O(trajectory within time ** 2) + O(trajectory)
            for (timestamp in earliestTimestamp until lastTimestamp step 5000) {
//                println("Time starts now!!! Iteration: $count")
//                println("Earliest Timestamp: $earliestTimestamp")
//                println("Last Timestamp: $lastTimestamp")
//                println("Timestamp: $timestamp")
                //for each of the aircraft (trajectories)
                logger.info { "$timestamp: Extracting relevant waypoint coordinates for current timestamp" }
                val geoPointPairMap = geoPointPairMutableMap(conflictForecastRequest, timestamp, geodeticCalc)
                //                println("geoPointPairMap: $geoPointPairMap")
                logger.info { "$timestamp: Finding lateral distance between different aircrafts pairs" }
                val distancePairs = getAircraftDistancePairs(geoPointPairMap, geodeticCalc)
                //                println("distancePairs: $distancePairs")

                logger.info { "$timestamp: Finding relevant regions to different aircrafts" }
                val geoPointRegionMap = evaluateAircraftRegions(geoPointPairMap, conflictForecastRequest, geodeticCalc)
                //                println("geoPointRegionMap: ${geoPointRegionMap.size}")
//                println("distancePairs: ${distancePairs.size}")
//                println("geoPointPairMap: ${geoPointPairMap.size}")

                logger.info { "$timestamp: Finding conflicts from lateral distance given the relevant regions and interpolated position" }
                findConflictsFromSeparationRegions(distancePairs, geoPointRegionMap, geoPointPairMap, conflictsResponse)
//                println("conflictsResponse: ${conflictsResponse.size}")

                logger.info { "$timestamp: Number of relevant aircrafts in current time: ${geoPointPairMap.size}" }
                logger.info { "$timestamp: Number of aircraft combinations lateral distances in current time: ${distancePairs.size}" }
                logger.info { "$timestamp: Number of conflicts from regions in current time: ${conflictsResponse.size}" }

                /*for each of the aircraft (trajectories),
                 given the waypoints, find the two waypoints in between the current time,
                 find the headingAndDistanceTo from the two waypoints and
                 from the initial waypoint, heading & dist, find the next position using nextPointFrom
                 find the distance_new from currentpoint to initial point
                 Calculate the speed_new = distance_new/time which 5 seconds
                 insert into mapA {id1: currentpoint_atcurrenttime, heading, distance_new, speed_new, initial_point}
                 --end the loop
                 Compute lateral distance:
                 Lateral distance between each aircraft at current time
                 = use distance function between all currentpoint_atcurrenttime
                 {(id1, id2): lateral_dist}
                 Compute each aircraft region:
                 {id1: distance_from_region1, distance_from_region2...}
                 find region with smallest dist,
                 if same dist, take one with smallest radius
                 {id1: region1, ...}
                 Check lateral separation breakdown:
                 lateral_dist_map -> iterate thru the entry -> query key condition:
                 if both same region -> check is lateral separation exceeded? -> if yes, retrieve from mapA, add to conflict(id1, id2, start1, end1, start2, end2)
                 if both different region -> get the larger separation requirement -> check is it exceeded? -> if yes add to conflict
                 */
            }
        }

        return ConflictForecastResponse(conflictsResponse)
        //return conflictForecastService.createConflict(conflictForecastRequest)
    }

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
    //                        println("Skipping aircraft pairs not inside a proper separationRegion")
    //                        println(geoPointRegionMap[aircrafts.id2])
    //                        println(geoPointRegionMap[aircrafts.id1])
    //                        println(geoPointPairMap[aircrafts.id2])
    //                        println(geoPointPairMap[aircrafts.id1])
                continue
    //                        throw IllegalArgumentException("One or both geo points are null: "
    //                                + aircrafts.id1 + " : " + aircrafts.id2)
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

    private fun evaluateAircraftRegions(
        geoPointPairMap: MutableMap<Int, GeoPointPair>,
        conflictForecastRequest: ConflictForecastRequest,
        geodeticCalc: GeodeticCalc
    ): MutableMap<Int, SeparationRequirement?> {
        val geoPointRegionMap = mutableMapOf<Int, SeparationRequirement?>()
        for (trajectory in geoPointPairMap) { // O(trajectory within time * region)
            var minRegionDistance: Double = Double.POSITIVE_INFINITY
            var selectedRegion: SeparationRequirement? =
                null//conflictForecastRequest.separationRequirements.maxBy { it.lateralSeparation }

            for (region in conflictForecastRequest.separationRequirements) {
                val distanceFromCenter: Double = geodeticCalc.distance(trajectory.value.geoPointCurrent, region.center)


                if (distanceFromCenter <= minRegionDistance
                    && distanceFromCenter < region.radius
                ) {
//                    if (trajectory.key == 368) {
//                        println("distanceFromCenter: ${trajectory.value.geoPointCurrent}")
//                        println("distanceFromCenter: $distanceFromCenter")
//                        println("minRegionDistance: $minRegionDistance")
//                    }
                    minRegionDistance = distanceFromCenter
                    selectedRegion = region
                }
            }

            geoPointRegionMap[trajectory.key] = selectedRegion
        }
        return geoPointRegionMap
    }

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
                //insert into mapA {id1: currentpoint_atcurrenttime, heading, distance_new, speed_new, initial_point}
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
//            println("Update the endTime of the existing conflict")
            conflictsResponse[existingConflictIndex] = conflictsResponse[existingConflictIndex].copy(
                conflictEndA = newConflict.conflictEndA,
                conflictEndB = newConflict.conflictEndB
            )
        } else {
            // Add the conflict if it doesn't already exist
            conflictsResponse.add(newConflict)
        }
    }

    fun findWaypointsAroundTimestamp(waypointsBefore: List<Waypoint>, waypointsAfter: List<Waypoint>, targetTimestamp: Long): Pair<TemporalGeoPoint?, TemporalGeoPoint?> {
//        println("waypoints before: $waypointsBefore")
//        println("waypoints after: $waypointsAfter")

        val waypointBefore = waypointsBefore.maxByOrNull { it.timestamp }
        val waypointAfter = waypointsAfter.minByOrNull { it.timestamp }
//        println("waypoints2 before: $waypointsBefore")
//        println("waypoints2 after: $waypointsAfter")

        val geoPointBefore: TemporalGeoPoint? = waypointBefore?.toTemporalGeoPoint()
        val geoPointAfter: TemporalGeoPoint? = waypointAfter?.toTemporalGeoPoint()
//        println("geoPointBefore before: $geoPointBefore")
//        println("geoPointAfter after: $geoPointAfter")
        return Pair(geoPointBefore, geoPointAfter)
    }

    fun Waypoint.toTemporalGeoPoint(): TemporalGeoPoint {
        return TemporalGeoPoint(lon, lat, timestamp)
    }

    fun interpolatePosition(geoPoint1: TemporalGeoPoint, geoPoint2: TemporalGeoPoint, targetTimestamp: Long, distance: Double): Double {
        // Calculate the time difference between the two points
        val timeDifference = geoPoint2.timestamp - geoPoint1.timestamp

        // Calculate the interpolation factor based on the target timestamp
        val interpolationFactor = (targetTimestamp - geoPoint1.timestamp).toDouble() / timeDifference.toDouble()

        // Return the interpolated distance
        return distance * interpolationFactor
    }

    fun convertToTemporalGeoPoint(geoPoint: IGeoPoint, timestamp: Long): TemporalGeoPoint {
        return TemporalGeoPoint(geoPoint.lon, geoPoint.lat, timestamp)
    }

}