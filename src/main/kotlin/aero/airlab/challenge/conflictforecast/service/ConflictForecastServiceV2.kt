package aero.airlab.challenge.conflictforecast.service

import aero.airlab.challenge.conflictforecast.api.ConflictForecastRequest
import aero.airlab.challenge.conflictforecast.api.ConflictForecastResponse

interface ConflictForecastServiceV2 {

    suspend fun createConflict(conflictForecastRequest: ConflictForecastRequest): ConflictForecastResponse
}