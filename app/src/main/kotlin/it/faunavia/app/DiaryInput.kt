package it.faunavia.app

import it.faunavia.domain.MAX_OBSERVATION_QUANTITY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val DiaryDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
internal val DiaryTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal data class ParsedDiaryInput(val observedAt: Instant, val quantity: Int)

internal fun parseDiaryInput(date: String, time: String, quantity: String, zoneId: ZoneId): ParsedDiaryInput {
    val localDate = runCatching { LocalDate.parse(date.trim(), DiaryDateFormatter) }
        .getOrElse { throw IllegalArgumentException("Inserisci una data valida nel formato AAAA-MM-GG.") }
    val localTime = runCatching { LocalTime.parse(time.trim(), DiaryTimeFormatter) }
        .getOrElse { throw IllegalArgumentException("Inserisci un'ora valida nel formato HH:MM.") }
    val count = quantity.trim().toIntOrNull()
        ?: throw IllegalArgumentException("Inserisci una quantità intera.")
    require(count in 1..MAX_OBSERVATION_QUANTITY) {
        "La quantità deve essere tra 1 e $MAX_OBSERVATION_QUANTITY."
    }
    return ParsedDiaryInput(localDate.atTime(localTime).atZone(zoneId).toInstant(), count)
}
