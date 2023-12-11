package aero.airlab.challenge.conflictforecast.web.rest

import aero.airlab.challenge.conflictforecast.api.*
import aero.airlab.challenge.conflictforecast.geospatial.*
import aero.airlab.challenge.conflictforecast.service.ConflictForecastService
import aero.airlab.challenge.conflictforecast.service.ConflictForecastServiceV2
import aero.airlab.challenge.conflictforecast.service.GeoJsonMapper
import io.github.oshai.kotlinlogging.KotlinLogging

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/v2/forecasts")
class ConflictForecastControllerV2(
    private val conflictForecastServiceV2: ConflictForecastServiceV2,
    private val geoJsonMapper: GeoJsonMapper
){

    private val logger = KotlinLogging.logger {}

    @PostMapping("/conflicts", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findConflictForecasts(@RequestBody conflictForecastRequest: ConflictForecastRequest): ConflictForecastResponse {
        logger.info { "Initiated processing for /v2/forecasts/conflicts API" }
        return conflictForecastServiceV2.createConflict(conflictForecastRequest)
    }

    @PostMapping("/conflict-features", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findConflictForecastFeatures(@RequestBody conflictForecastRequest: ConflictForecastRequest): Map<String, Any> {
        logger.info { "Initiated processing for /v2/forecasts/conflict-features API" }
        return geoJsonMapper.createConflictsAsFeatureCollectionsV2(conflictForecastRequest)
    }

}