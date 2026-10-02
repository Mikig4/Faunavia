package it.faunavia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import it.faunavia.exploration.FAUNAVIA_HTTP_USER_AGENT
import it.faunavia.exploration.SpeciesMetadataCache
import it.faunavia.exploration.safeCommonsMedia
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Normalized presentation cache; never writes an implicitly selected taxonomy record into Room. */
internal class SharedPreferencesSpeciesMetadataCache(context: Context) : SpeciesMetadataCache {
    private val preferences = context.getSharedPreferences("species-presentation-v1", Context.MODE_PRIVATE)
    @Synchronized override fun read(key: String): String? = preferences.getString(key, null)
    @Synchronized override fun save(key: String, normalized: String) {
        val all = preferences.all
        val oldest = if (key !in all && all.size >= 256) all.minByOrNull {
            runCatching { JSONObject(it.value as String).getString("cachedAt") }.getOrDefault("")
        }?.key else null
        preferences.edit { oldest?.let(::remove); putString(key, normalized) }
    }
}

internal interface SpeciesMapImageLoader { fun load(url: String): Bitmap }

internal class CommonsMapImageLoader(context: Context) : SpeciesMapImageLoader {
    private val client = OkHttpClient.Builder().cache(Cache(File(context.cacheDir, "species-map-images"), 32L * 1024 * 1024))
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).followRedirects(false).build()

    override fun load(url: String): Bitmap {
        require(safeCommonsMedia(url)) { "Map media origin is not permitted" }
        val request = Request.Builder().url(url).header("User-Agent", FAUNAVIA_HTTP_USER_AGENT).build()
        val response = try { client.newCall(request).execute() } catch (_: IOException) {
            client.newCall(request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()).execute()
        }
        val bytes = response.use {
            if (!it.isSuccessful) throw IOException("Map image unavailable")
            val body = it.body ?: throw IOException("Missing map image")
            if (body.contentLength() > 4L * 1024 * 1024) throw IOException("Map image too large")
            if (body.contentType()?.type != "image") throw IOException("Unexpected map media")
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var count = input.read(buffer)
                while (count >= 0) {
                    if (output.size() + count > 4 * 1024 * 1024) throw IOException("Map image too large")
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                output.toByteArray()
            }
        }
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        if (dimensions.outWidth !in 1..8_192 || dimensions.outHeight !in 1..8_192) throw IOException("Invalid map dimensions")
        val options = BitmapFactory.Options()
        while (maxOf(dimensions.outWidth, dimensions.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1_600) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("Invalid map image")
    }
}
