package aero.airlab.challenge.conflictforecast.service

import aero.airlab.challenge.conflictforecast.api.ConflictForecastRequest
import aero.airlab.challenge.conflictforecast.api.ConflictForecastResponse
import org.springframework.stereotype.Service

@Service
class ConflictForecastServiceImpl : ConflictForecastService {

    override fun createConflict(conflictForecastRequest: ConflictForecastRequest): ConflictForecastResponse {
        return ConflictForecastResponse(emptyList())
    }
}