package aero.airlab.challenge.conflictforecast.geomodels

class SeparationRegionFeatures {
    data class SeparationRegionFeatureCollection(
        val type: String = "FeatureCollection",
        val features: List<Map<String, Any>>
    )

    data class SeparationRegionFeature(
        val type: String = "Feature",
        val geometry: SeparationRegionGeometry,
        val properties: SeparationRegionProperties
    )

    data class SeparationRegionGeometry(
        val type: String = "Polygon",
        val coordinates: List<List<List<Double>>>
    )

    data class SeparationRegionProperties(
        val radius: Double,
        val lateralSeparation: Double
    )


}