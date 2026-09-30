package it.faunavia.app

import android.content.Context
import androidx.core.content.edit
import it.faunavia.domain.GeoPoint
import it.faunavia.exploration.PlaceCache
import it.faunavia.exploration.PlaceCandidate
import it.faunavia.exploration.PlaceKind
import it.faunavia.route.GeoBounds
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Bounded, checkout-local place cache. Only public place metadata is stored, never user routes. */
class PlaceCachePreferences(context: Context) : PlaceCache {
    private val preferences = context.getSharedPreferences("place-cache-v1", Context.MODE_PRIVATE)

    override fun read(query: String): List<PlaceCandidate>? {
        val raw = preferences.getString(query.lowercase(Locale.ROOT), null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            if (System.currentTimeMillis() - root.getLong("at") > 30L * 24 * 60 * 60 * 1_000) return null
            val items = root.getJSONArray("places")
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val bounds = item.optJSONObject("bounds")?.let {
                    GeoBounds(it.getDouble("w"), it.getDouble("s"), it.getDouble("e"), it.getDouble("n"))
                }
                PlaceCandidate(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    kind = PlaceKind.valueOf(item.getString("kind")),
                    countryCode = item.optString("country").ifBlank { null },
                    center = GeoPoint(item.getDouble("lat"), item.getDouble("lon")),
                    bounds = bounds,
                )
            }
        }.getOrNull()
    }

    override fun write(query: String, places: List<PlaceCandidate>) {
        val items = JSONArray()
        places.take(5).forEach { place ->
            items.put(JSONObject().apply {
                put("id", place.id)
                put("name", place.name)
                put("kind", place.kind.name)
                put("country", place.countryCode.orEmpty())
                put("lat", place.center.latitude)
                put("lon", place.center.longitude)
                place.bounds?.let { bounds ->
                    put("bounds", JSONObject().apply {
                        put("w", bounds.west)
                        put("s", bounds.south)
                        put("e", bounds.east)
                        put("n", bounds.north)
                    })
                }
            })
        }
        preferences.edit {
            putString(query.lowercase(Locale.ROOT), JSONObject().put("at", System.currentTimeMillis())
                .put("places", items).toString())
        }
    }
}
