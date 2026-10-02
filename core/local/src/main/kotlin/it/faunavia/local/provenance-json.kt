package it.faunavia.local

import it.faunavia.domain.Provenance
import java.time.Instant
import org.json.JSONObject

internal fun Provenance.toJson(): JSONObject = JSONObject()
    .put("source", source)
    .put("recordId", recordId)
    .put("query", query)
    .put("retrievedAt", retrievedAt.toString())
    .put("license", license)
    .put("attribution", attribution)
    .put("quality", quality)
    .put("version", version)

internal fun JSONObject.toProvenance(): Provenance = Provenance(
    source = getString("source"),
    recordId = getString("recordId"),
    query = getString("query"),
    retrievedAt = Instant.parse(getString("retrievedAt")),
    license = getString("license"),
    attribution = getString("attribution"),
    quality = getString("quality"),
    version = getString("version"),
)
