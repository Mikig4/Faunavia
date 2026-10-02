package it.faunavia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var summaryLink by mutableStateOf<SummaryLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        summaryLink = SummaryLink.read(intent)
        setContent {
            val services = application as FaunaviaApplication
            CompositionLocalProvider(LocalSpeciesMetadata provides services.speciesMetadata,
                LocalSpeciesImageLoader provides services.speciesImageLoader) {
                FaunaviaTheme {
                    FaunaviaApp(summaryLink = summaryLink, onSummaryLinkConsumed = {
                        summaryLink = null
                        SummaryLink.clear(intent)
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        summaryLink = SummaryLink.read(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try { (application as FaunaviaApplication).reminderPreferences.reconcile() }
            catch (failure: Exception) { reminderRecoveryWarning(failure, "Cannot reconcile reminder on resume.") }
        }
    }
}
