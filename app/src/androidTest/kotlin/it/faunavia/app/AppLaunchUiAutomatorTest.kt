package it.faunavia.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchUiAutomatorTest {
    @Test
    fun launcherStartsTheInstallableApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        device.pressHome()
        val launchIntent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertNotNull("The debug APK must expose a launcher activity.", launchIntent)
        targetContext.startActivity(launchIntent)

        val launched = device.wait(Until.hasObject(By.text("Faunavia")), 10_000)
        check(launched) { "Faunavia did not become visible after launcher start." }
    }
}
