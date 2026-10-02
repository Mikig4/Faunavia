package it.faunavia.exploration

import it.faunavia.domain.TripPlace
import it.faunavia.domain.TripRoute

sealed interface TripRoutingResult {
    data class Routes(val choices: List<TripRoute>) : TripRoutingResult
    data object Unavailable : TripRoutingResult
    data object NoRoute : TripRoutingResult
}

fun interface TripRouting {
    suspend fun plan(departure: TripPlace, destination: TripPlace): TripRoutingResult
}
