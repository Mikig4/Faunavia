package it.faunavia.app

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeGoldenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeMatchesVersionedColorSignature() {
        val image = composeRule.onNodeWithTag("screen-home").captureToImage()
        val pixels = image.toPixelMap()
        val baseline = readBaseline()
        val samples = baseline.getJSONArray("samples")

        repeat(samples.length()) { index ->
            val sample = samples.getJSONObject(index)
            val x = ((pixels.width - 1) * sample.getDouble("xRatio")).toInt()
            val y = ((pixels.height - 1) * sample.getDouble("yRatio")).toInt()
            val actual = pixels[x, y].toArgb().toUInt().toString(16).padStart(8, '0').uppercase()
            assertEquals(sample.getString("argb"), actual)
        }

        val outputDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "golden-output",
        ).apply { mkdirs() }
        File(outputDirectory, "home-actual.png").outputStream().use { stream ->
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun readBaseline(): JSONObject {
        val context: Context = InstrumentationRegistry.getInstrumentation().context
        val text = context.assets.open("golden/home-v1.json").bufferedReader().use { it.readText() }
        return JSONObject(text)
    }
}
