package aero.airlab.challenge.conflictforecast.web.rest

import aero.airlab.challenge.conflictforecast.api.*
import aero.airlab.challenge.conflictforecast.geospatial.*
import aero.airlab.challenge.conflictforecast.service.ConflictForecastService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.Double.max

@RestController
@RequestMapping("/v1")
class ConflictForecastController(
    private val conflictForecastService: ConflictForecastService
){
    @PostMapping("/test")
    fun findConflictForecasts(@RequestBody conflictForecastRequest: ConflictForecastRequest): ConflictForecastResponse {

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

        println("Earliest Timestamp: $earliestTimestamp")
        println("Last Timestamp: $lastTimestamp")
        val conflictsResponse = mutableListOf<Conflict>()
        // Loop from the current timestamp to the end timestamp, increasing by 5 seconds each iteration
        if (earliestTimestamp != null && lastTimestamp != null) {
            earliestTimestamp += 5
            lastTimestamp += 5
            //init empty conflict array
            var count: Int = 0
            for (timestamp in earliestTimestamp until lastTimestamp step 5) {
//                println("Time starts now!!! Iteration: $count")
//                println("Earliest Timestamp: $earliestTimestamp")
//                println("Last Timestamp: $lastTimestamp")
                count +=1
//                println("Timestamp: $timestamp")
                //for each of the aircraft (trajectories)
                val geoPointPairMap = mutableMapOf<Int, GeoPointPair>()
                for (trajectory in conflictForecastRequest.trajectories) {
                    val waypointsBefore = trajectory.waypoints.filter { it.timestamp <= timestamp }
                    val waypointsAfter = trajectory.waypoints.filter { it.timestamp > timestamp }
                    if (waypointsBefore.isEmpty() || waypointsAfter.isEmpty()){
                        continue
                    }
                    //given the waypoints, find the two waypoints in between the current time,
                    val (geoPointBefore: TemporalGeoPoint?, geoPointAfter: TemporalGeoPoint?) = findWaypointsAroundTimestamp(waypointsBefore, waypointsAfter, targetTimestamp = timestamp)
                    //find the headingAndDistanceTo from the two waypoints and
                    if (geoPointBefore != null && geoPointAfter != null) {
                        val (heading, distance) = geodeticCalc.headingAndDistanceTo(geoPointBefore, geoPointAfter)
                        //interpolate based on the timestamp differences for the distance at current timestamp
                        val interpolatedDistance: Double = interpolatePosition(geoPointBefore, geoPointAfter, timestamp, distance)
                        //from the initial waypoint, heading & dist, find the next position using nextPointFrom
                        val geoPointAtCurrentTime: TemporalGeoPoint = convertToTemporalGeoPoint(geodeticCalc.nextPointFrom(geoPointBefore, heading, interpolatedDistance), timestamp)
                        //insert into mapA {id1: currentpoint_atcurrenttime, heading, distance_new, speed_new, initial_point}
                        val geoPointPair = GeoPointPair(geoPointBefore, geoPointAtCurrentTime)
                        geoPointPairMap[trajectory.id] = geoPointPair

                    } else {
                        // Handle the case where either geoPointBefore or geoPointAfter is null
                        throw IllegalArgumentException("One or both geo points are null.")
                    }
                }
//                println("geoPointPairMap: $geoPointPairMap")
                val distancePairs = mutableListOf<AircraftDistancePair>()
                for (key in geoPointPairMap.keys) {
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
//                println("distancePairs: $distancePairs")

                val geoPointRegionMap = mutableMapOf<Int, SeparationRequirement?>()
                for (trajectory in geoPointPairMap) {
                    var minRegionDistance: Double = Double.POSITIVE_INFINITY
                    var selectedRegion: SeparationRequirement? = null//conflictForecastRequest.separationRequirements.maxBy { it.lateralSeparation }

                    for (region in conflictForecastRequest.separationRequirements) {
                        val distanceFromCenter: Double = geodeticCalc.distance(trajectory.value.geoPointCurrent, region.center)
//                        if (trajectory.key == 368) {
//                            println("distanceFromCenter: $distanceFromCenter")
//                            println("minRegionDistance: $minRegionDistance")
//                        }

                        if (distanceFromCenter <= minRegionDistance
                            && distanceFromCenter < region.radius) {
                            minRegionDistance = distanceFromCenter
                            selectedRegion = region
                        }
                    }

                    geoPointRegionMap[trajectory.key] = selectedRegion
                }
//                println("geoPointRegionMap: ${geoPointRegionMap.size}")
//                println("distancePairs: ${distancePairs.size}")
//                println("geoPointPairMap: ${geoPointPairMap.size}")


                for (aircrafts in distancePairs) {
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
                            && max(
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
//                println("conflictsResponse: ${conflictsResponse.size}")


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

    private fun addConflictHandler(
        aircrafts: AircraftDistancePair,
        geoPointPairMap: MutableMap<Int, GeoPointPair>,
        conflictsResponse: MutableList<Conflict>
    ) {
        val newConflict = Conflict(
            aircrafts.id1,
            geoPointPairMap[aircrafts.id1]!!.geoPointBefore,
            geoPointPairMap[aircrafts.id1]!!.geoPointCurrent,
            aircrafts.id2,
            geoPointPairMap[aircrafts.id2]!!.geoPointBefore,
            geoPointPairMap[aircrafts.id2]!!.geoPointCurrent
        )
        val existingConflictIndex = conflictsResponse.indexOfFirst {
            it.trajectoryA == newConflict.trajectoryA &&
                    it.conflictStartA.timestamp == newConflict.conflictStartA.timestamp &&
                    it.conflictStartA.lat == newConflict.conflictStartA.lat &&
                    it.conflictStartA.lon == newConflict.conflictStartA.lon &&
                    it.conflictStartB.timestamp == newConflict.conflictStartB.timestamp &&
                    it.conflictStartB.lat == newConflict.conflictStartB.lat &&
                    it.conflictStartB.lon == newConflict.conflictStartB.lon &&
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

    fun addToTemporalGeoPointMap(
        map: MutableMap<Int, MutableList<TemporalGeoPoint>>,
        temporalGeoPoint1: TemporalGeoPoint,
        temporalGeoPoint2: TemporalGeoPoint,
        id: Int
    ) {

        // Add the TemporalGeoPoints to the map
        map.computeIfAbsent(id) { mutableListOf() }.apply {
            add(temporalGeoPoint1)
            add(temporalGeoPoint2)
        }
    }

}