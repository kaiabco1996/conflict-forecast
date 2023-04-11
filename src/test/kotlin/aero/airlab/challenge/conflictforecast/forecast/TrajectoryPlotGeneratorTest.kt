package aero.airlab.challenge.conflictforecast.forecast

import aero.airlab.challenge.conflictforecast.api.Waypoint
import aero.airlab.challenge.conflictforecast.geospatial.GeoPoint
import aero.airlab.challenge.conflictforecast.geospatial.GeodeticCalc
import io.kotest.assertions.asClue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


class TrajectoryPlotGeneratorTest {
    private val geoCalc = GeodeticCalc(GeoPoint.ZERO)

    @Test
    fun `test with one plot exactly at a waypoint`() {
        val trajectory = listOf(
            Waypoint(0.0, 0.1, 0),
            Waypoint(0.0, 0.0, 30000),
            Waypoint(0.1, 0.0, 60000),
        )
        val generator = TrajectoryPlotGenerator(trajectory, 10000, 60000, GeodeticCalc(GeoPoint.ZERO))
        val plots = generateSequence { generator.nextPlot(10000) }.toList()
        plots.forEach { println(it) }
        generator.completed shouldBe true
        generator.nextPlot(10000).shouldBeNull()
        plots.size shouldBe 5
        plots[0].asClue {
            val distanceToWaypoint1 = geoCalc.distance(trajectory[0], trajectory[1])
            val speedToWaypoint1 = distanceToWaypoint1 / 30000
            val expectedPos = geoCalc.nextPointFrom(GeoPoint(0.0, 0.1), 180.0, speedToWaypoint1 * 20000)

            it.position.timestamp shouldBe 20000
            it.position.lon shouldBe (expectedPos.lon plusOrMinus 0.001)
            it.position.lat shouldBe (expectedPos.lat plusOrMinus 0.001)
            it.lateralSpeed shouldBe ((speedToWaypoint1 * 1000) plusOrMinus 0.001)
            it.heading shouldBe 180.0
        }
        plots[1].asClue {
            it.position.timestamp shouldBe 30000
            it.position.lon shouldBe (trajectory[1].lon plusOrMinus 0.001)
            it.position.lat shouldBe (trajectory[1].lat plusOrMinus 0.001)
        }
        plots[4].asClue {
            it.position.timestamp shouldBe 60000
            it.position.lon shouldBe (trajectory[2].lon plusOrMinus 0.001)
            it.position.lat shouldBe (trajectory[2].lat plusOrMinus 0.001)
        }
    }

    @Test
    fun `test end time before last waypoint`() {
        val trajectory = listOf(
            Waypoint(0.0, 0.1, 0),
            Waypoint(0.0, 0.0, 30000),
            Waypoint(0.1, 0.0, 60000),
        )
        val generator = TrajectoryPlotGenerator(trajectory, 10000, 40000, GeodeticCalc(GeoPoint.ZERO))
        val plots = generateSequence { generator.nextPlot(10000) }.toList()
        plots.forEach { println(it) }
        generator.completed shouldBe true
        generator.nextPlot(10000).shouldBeNull()
        plots.size shouldBe 3
    }

    @Test
    fun `test with plots overshooting waypoints`() {
        val trajectory = listOf(
            Waypoint(0.0, 0.1, 0),
            Waypoint(0.0, 0.0, 30000),
            Waypoint(0.1, 0.0, 60000),
        )
        val generator = TrajectoryPlotGenerator(trajectory, 0, 60000, GeodeticCalc(GeoPoint.ZERO))
        val plots = generateSequence { generator.nextPlot(25000) }.toList()
        plots.forEach { println(it) }
        plots.size shouldBe 2
        plots[0].asClue {
            val distanceToWaypoint1 = geoCalc.distance(trajectory[0], trajectory[1])
            val speedToWaypoint1 = distanceToWaypoint1 / 30000
            val expectedPos = geoCalc.nextPointFrom(GeoPoint(0.0, 0.1), 180.0, speedToWaypoint1 * 25000)

            it.position.timestamp shouldBe 25000
            it.position.lon shouldBe (expectedPos.lon plusOrMinus 0.001)
            it.position.lat shouldBe (expectedPos.lat plusOrMinus 0.001)
            it.lateralSpeed shouldBe ((speedToWaypoint1 * 1000) plusOrMinus 0.001)
            it.heading shouldBe 180.0
        }
        plots[1].asClue {
            val distanceToWaypoint2 = geoCalc.distance(trajectory[1], trajectory[2])
            val speedToWaypoint2 = distanceToWaypoint2 / 30000
            val expectedPos = geoCalc.nextPointFrom(GeoPoint(0.0, 0.0), 90.0, speedToWaypoint2 * 20000)

            it.position.timestamp shouldBe 50000
            it.position.lon shouldBe (expectedPos.lon plusOrMinus 0.001)
            it.position.lat shouldBe (expectedPos.lat plusOrMinus 0.001)
            it.lateralSpeed shouldBe ((speedToWaypoint2 * 1000) plusOrMinus 0.001)
            it.heading shouldBe 90.0
        }
    }

    @Test
    fun `test overshooting 2 waypoints`() {
        val trajectory = listOf(
            Waypoint(0.0, 0.1, 0),
            Waypoint(0.0, 0.05, 15000),
            Waypoint(0.0, 0.0, 30000),
            Waypoint(0.05, 0.0, 45000),
            Waypoint(0.1, 0.0, 90000),
        )
        val generator = TrajectoryPlotGenerator(trajectory, 10000, 90000, GeodeticCalc(GeoPoint.ZERO))
        val plots = generateSequence { generator.nextPlot(30000) }.toList()
        plots.forEach { println(it) }
        plots.size shouldBe 2
        plots[0].asClue {
            val distanceToWaypoint3 = geoCalc.distance(trajectory[2], trajectory[3])
            val speedToWaypoint3 = distanceToWaypoint3 / 15000
            val expectedPos = geoCalc.nextPointFrom(GeoPoint(0.0, 0.0), 90.0, speedToWaypoint3 * 10000)

            it.position.timestamp shouldBe 40000
            it.position.lon shouldBe (expectedPos.lon plusOrMinus 0.001)
            it.position.lat shouldBe (expectedPos.lat plusOrMinus 0.001)
            it.lateralSpeed shouldBe ((speedToWaypoint3 * 1000) plusOrMinus 0.001)
            it.heading shouldBe 90.0
        }
        plots[1].asClue {
            val distanceToWaypoint4 = geoCalc.distance(trajectory[3], trajectory[4])
            val speedToWaypoint4 = distanceToWaypoint4 / 45000
            val expectedPos = geoCalc.nextPointFrom(GeoPoint(0.05, 0.0), 90.0, speedToWaypoint4 * 25000)

            it.position.timestamp shouldBe 70000
            it.position.lon shouldBe (expectedPos.lon plusOrMinus 0.001)
            it.position.lat shouldBe (expectedPos.lat plusOrMinus 0.001)
            it.lateralSpeed shouldBe ((speedToWaypoint4 * 1000) plusOrMinus 0.001)
            it.heading shouldBe 90.0
        }
    }

    @Test
    fun `test start time before trajectory`() {
        val trajectory = listOf(
            Waypoint(0.0, 0.1, 10000),
            Waypoint(0.0, 0.0, 20000),
            Waypoint(0.1, 0.0, 30000),
        )
        val generator = TrajectoryPlotGenerator(trajectory, 0, 30000, GeodeticCalc(GeoPoint.ZERO))
        generator.nextPlot(5000).shouldBeNull()
        val plots = generateSequence { generator.nextPlot(5000) }.toList()
        plots.forEach { println(it) }
        plots.size shouldBe 5
    }
}