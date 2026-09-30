package it.faunavia.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.faunavia.domain.GeoPoint
import it.faunavia.exploration.FAUNAVIA_HTTP_USER_AGENT
import it.faunavia.route.RouteAnalysis
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.max

interface ExplorationMapAdapter {
    @Composable fun Render(analysis: RouteAnalysis)
}

object MapLibreExplorationMapAdapter : ExplorationMapAdapter {
    @Composable
    override fun Render(analysis: RouteAnalysis) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val mapView = remember(analysis.fingerprint) {
            MapLibre.getInstance(context)
            HttpRequestUtil.setOkHttpClient(
                OkHttpClient.Builder()
                    .cache(Cache(java.io.File(context.cacheDir, "maplibre-http"), 64L * 1024 * 1024))
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder()
                            .header("User-Agent", FAUNAVIA_HTTP_USER_AGENT)
                            .build())
                    }
                    .addNetworkInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        if (chain.request().url.host == "tile.openstreetmap.org" &&
                            response.header("Cache-Control") == null && response.header("Expires") == null
                        ) response.newBuilder().header("Cache-Control",
                            CacheControl.Builder().maxAge(7, TimeUnit.DAYS).build().toString()).build()
                        else response
                    }
                    .build(),
            )
            MapView(context).apply { onCreate(null) }
        }
        DisposableEffect(mapView, lifecycleOwner) {
            var started = false
            var resumed = false
            fun start() { if (!started) { mapView.onStart(); started = true } }
            fun resume() { start(); if (!resumed) { mapView.onResume(); resumed = true } }
            fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
            fun stop() { pause(); if (started) { mapView.onStop(); started = false } }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> start()
                    Lifecycle.Event.ON_RESUME -> resume()
                    Lifecycle.Event.ON_PAUSE -> pause()
                    Lifecycle.Event.ON_STOP -> stop()
                    Lifecycle.Event.ON_DESTROY -> stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
            else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                stop()
                mapView.onDestroy()
            }
        }
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        val style = mapStyle(context.assets.open("osm-raster-style.json").bufferedReader().use { it.readText() }, analysis)
                        map.setStyle(Style.Builder().fromJson(style))
                        val bounds = analysis.bounds
                        val span = max(bounds.east - bounds.west, bounds.north - bounds.south)
                        val zoom = when {
                            span > 5 -> 5.0
                            span > 1 -> 7.0
                            span > 0.2 -> 9.0
                            else -> 11.0
                        }
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng((bounds.south + bounds.north) / 2, (bounds.west + bounds.east) / 2))
                            .zoom(zoom)
                            .build()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(260.dp).testTag("explore-map"),
            update = {},
        )
    }
}

/** Renders only route/sample/area geometry; source occurrence coordinates are never exposed. */
internal fun mapStyle(base: String, analysis: RouteAnalysis): String {
    val root = JSONObject(base)
    val sources = root.getJSONObject("sources")
    val layers = root.getJSONArray("layers")
    val corridorFeatures = JSONArray()
    analysis.corridorPortions.forEach { portion ->
        corridorFeatures.put(feature("Polygon", JSONArray().put(coordinates(portion.polygon))))
    }
    sources.put("corridor", source(corridorFeatures))
    layers.put(JSONObject().put("id", "corridor-fill").put("type", "fill").put("source", "corridor")
        .put("paint", JSONObject().put("fill-color", "#2A714E").put("fill-opacity", 0.17)))
    val routeFeatures = JSONArray()
    if (analysis.samples.size > 1) {
        routeFeatures.put(feature("LineString", coordinates(analysis.samples)))
    }
    sources.put("route", source(routeFeatures))
    layers.put(JSONObject().put("id", "route-line").put("type", "line").put("source", "route")
        .put("paint", JSONObject().put("line-color", "#174F36").put("line-width", 3)))
    val samples = JSONArray()
    analysis.samples.take(100).forEach { point -> samples.put(feature("Point", coordinate(point))) }
    sources.put("samples", source(samples))
    layers.put(JSONObject().put("id", "sample-points").put("type", "circle").put("source", "samples")
        .put("paint", JSONObject().put("circle-color", "#F6FAF3").put("circle-stroke-color", "#174F36")
            .put("circle-stroke-width", 2).put("circle-radius", 4)))
    return root.toString()
}

private fun source(features: JSONArray): JSONObject = JSONObject()
    .put("type", "geojson")
    .put("data", JSONObject().put("type", "FeatureCollection").put("features", features))

private fun feature(type: String, coordinates: JSONArray): JSONObject = JSONObject()
    .put("type", "Feature")
    .put("properties", JSONObject())
    .put("geometry", JSONObject().put("type", type).put("coordinates", coordinates))

private fun coordinate(point: GeoPoint): JSONArray = JSONArray().put(point.longitude).put(point.latitude)

private fun coordinates(points: List<GeoPoint>): JSONArray = JSONArray().apply {
    points.forEach { put(coordinate(it)) }
}
