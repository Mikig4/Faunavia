package it.faunavia.app

import android.content.Context
import it.faunavia.exploration.SpeciesProfileCache
import it.faunavia.exploration.SpeciesProfileCodec

/** At most 64 normalized viewed profiles (8 MiB worst case); no personal records or remote images. */
internal class SpeciesProfileCachePreferences(context: Context) : SpeciesProfileCache {
    private val preferences = context.getSharedPreferences("species-profiles-v1", Context.MODE_PRIVATE)
    @Synchronized override fun read(key: String): String? = preferences.getString(key, null)
    // The KTX edit helper discards commit's Boolean; disk failure must reach the visible retry state.
    @android.annotation.SuppressLint("UseKtx")
    @Synchronized override fun save(key: String, value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= SpeciesProfileCodec.MAX_BYTES)
        val editor = preferences.edit()
        val oldest = preferences.all.filterKeys { it != key }.entries.sortedBy { (_, raw) ->
            runCatching { org.json.JSONObject(raw as String).getString("cachedAt") }.getOrDefault("")
        }
        oldest.take((oldest.size - 63).coerceAtLeast(0)).forEach { editor.remove(it.key) }
        check(editor.putString(key, value).commit()) { "Profile cache write failed" }
    }
}
