package aero.airlab.challenge.conflictforecast.forecast

import aero.airlab.challenge.conflictforecast.api.Waypoint
import aero.airlab.challenge.conflictforecast.geospatial.GeodeticCalc
import aero.airlab.challenge.conflictforecast.geospatial.TemporalGeoPoint

class TrajectoryPlotGenerator(
    private val trajectory: List<Waypoint>,
    startTime: Long,
    private val endTime: Long,
    private val geoCalc: GeodeticCalc
) {
    data class Plot(val position: TemporalGeoPoint, val lateralSpeed: Double, val heading: Double)

    private var currentTime = startTime
    private var currentWaypointIdx = -1
    private var currentHeading = 0.0
    private var currentLateralSpeed = 0.0
    var completed = false
        private set

    init {
        if (trajectory.size < 2) {
            throw IllegalArgumentException("Trajectory must have at least 2 points")
        }
        if (!trajectory.zipWithNext().all { (a, b) -> (b.timestamp - a.timestamp) > 1000 }) {
            throw IllegalArgumentException("Waypoints are not in ascending temporal order or not at least 1 second apart")
        }
    }

    fun nextPlot(timePassed: Long): Plot? {
        if (completed) return null

        currentTime += timePassed

        if (currentTime > endTime) {
            completed = true
            return null
        }

        if (currentWaypointIdx < 0 && trajectory[0].timestamp > currentTime) return null

        if (currentWaypointIdx < 0 || trajectory[currentWaypointIdx + 1].timestamp < currentTime) {
            val newWaypointIdx = trajectory.indexOfFirst { it.timestamp > currentTime } - 1
            if (newWaypointIdx !in (0..(trajectory.size - 2))) {
                completed = true
                return null
            }
            currentWaypointIdx = newWaypointIdx
            updateHeadingAndSpeed()
        }
        val totalTimePassed = currentTime - trajectory[currentWaypointIdx].timestamp
        val currentLateralPos = geoCalc.nextPointFrom(
            trajectory[currentWaypointIdx], currentHeading, currentLateralSpeed * totalTimePassed / 1000.0)
        return Plot(
            TemporalGeoPoint(
                currentLateralPos.lon,
                currentLateralPos.lat,
                currentTime
            ), currentLateralSpeed, currentHeading
        )
    }

    private fun updateHeadingAndSpeed() {
        val currentWaypoint = trajectory[currentWaypointIdx]
        val nextWaypoint = trajectory[currentWaypointIdx + 1]
        val (heading, lateralDistance) = geoCalc.headingAndDistanceTo(currentWaypoint, nextWaypoint)
        currentHeading = heading
        val timeToNextWaypoint = (nextWaypoint.timestamp - currentWaypoint.timestamp) / 1000.0
        currentLateralSpeed = lateralDistance / timeToNextWaypoint
    }
}