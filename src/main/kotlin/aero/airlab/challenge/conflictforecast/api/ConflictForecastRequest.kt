package aero.airlab.challenge.conflictforecast.api

data class ConflictForecastRequest(
    val trajectories: List<Trajectory>,
    /**
     * List of separation requirements.
     *
     * Regions earlier in the list has higher priority.
     * Aircraft not in any region do not need to be separated and
     * will not cause conflict.
     */
    val separationRequirements: List<SeparationRequirement>
)
