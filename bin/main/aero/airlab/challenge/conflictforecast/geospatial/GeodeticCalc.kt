package aero.airlab.challenge.conflictforecast.geospatial

import org.geotools.geometry.jts.JTS
import org.geotools.geometry.jts.JTSFactoryFinder
import org.geotools.referencing.CRS
import org.geotools.referencing.GeodeticCalculator
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.locationtech.jts.geom.Coordinate

private fun IGeoPoint.toCoordinate() = Coordinate(lon, lat)

/**
 * Note this class is NOT thread-safe.
 */
class GeodeticCalc(refPt: IGeoPoint) {

    companion object {
        /**
         * Returns a new instance of [GeodeticCalc] centered on WSSS.
         */
        fun geodeticCalcWSSS() = GeodeticCalc(GeoPoint(104.0, 1.35))
    }

    private val geoCalc = GeodeticCalculator()
    private val autoCRS = CRS.decode("AUTO:42001,${refPt.lon},${refPt.lat}")
    private val transform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, autoCRS)
    private val geometryFactory = JTSFactoryFinder.getGeometryFactory()

    /**
     * @param pt Starting point
     * @param heading Direction to move in degrees (0 - 360)
     * @param distance Distance to move in metres
     * @return The destination point
     */
    fun nextPointFrom(pt: IGeoPoint, heading: Double, distance: Double): GeoPoint {
        geoCalc.setStartingGeographicPoint(pt.lon, pt.lat)
        geoCalc.setDirection(heading, distance)
        val pt2 = geoCalc.destinationGeographicPoint
        return GeoPoint(pt2.x, pt2.y)
    }

    /**
     * Returns the shortest distance between the two points in metres
     */
    fun distance(lonLat1: IGeoPoint, lonLat2: IGeoPoint): Double {
        geoCalc.setStartingGeographicPoint(lonLat1.lon, lonLat1.lat)
        geoCalc.setDestinationGeographicPoint(lonLat2.lon, lonLat2.lat)
        return geoCalc.orthodromicDistance
    }

    /**
     * Returns true if point is inside polygon.
     *
     * @param polygon First and last points should be the same
     */
    fun isPointInPolygon(pt: IGeoPoint, polygon: List<IGeoPoint>): Boolean {
        val jtsPolygon = geometryFactory.createPolygon(polygon.map { it.toCoordinate() }.toTypedArray())
        val point = geometryFactory.createPoint(pt.toCoordinate())
        val gPolygon = JTS.transform(jtsPolygon, transform)
        val gPoint = JTS.transform(point, transform)
        return gPoint.within(gPolygon)
    }

    /**
     * Returns the heading to reach [endPt] from [startPt] in degrees (0 - 360)
     */
    fun headingTo(startPt: IGeoPoint, endPt: IGeoPoint): Double {
        geoCalc.setStartingGeographicPoint(startPt.lon, startPt.lat)
        geoCalc.setDestinationGeographicPoint(endPt.lon, endPt.lat)
        val az = geoCalc.azimuth
        return if (az < 0) 360 + az else az
    }

    /**
     * Returns the heading in degrees (0 - 360) and distance in metres to reach [endPt] from [startPt].
     */
    fun headingAndDistanceTo(startPt: IGeoPoint, endPt: IGeoPoint): Pair<Double, Double> {
        geoCalc.setStartingGeographicPoint(startPt.lon, startPt.lat)
        geoCalc.setDestinationGeographicPoint(endPt.lon, endPt.lat)
        val az = geoCalc.azimuth
        return Pair(if (az < 0) 360 + az else az, geoCalc.orthodromicDistance)
    }
}
