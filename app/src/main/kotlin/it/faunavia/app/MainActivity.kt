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
    private var backupReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        summaryLink = SummaryLink.read(intent)
        backupReport = savedInstanceState?.getString("backup-report") ?: if (intent.getBooleanExtra("backup-restored", false))
            (application as FaunaviaApplication).localBackup.lastMessage else null
        intent.removeExtra("backup-restored")
        setContent {
            val services = application as FaunaviaApplication
            CompositionLocalProvider(LocalSpeciesMetadata provides services.speciesMetadata,
                LocalSpeciesProfiles provides services.speciesProfiles,
                LocalSpeciesImageLoader provides services.speciesImageLoader) {
                FaunaviaTheme {
                    FaunaviaApp(summaryLink = summaryLink, onSummaryLinkConsumed = {
                        summaryLink = null
                        SummaryLink.clear(intent)
                    })
                    backupReport?.let { report ->
                        androidx.compose.material3.AlertDialog(onDismissRequest = { backupReport = null },
                            title = { androidx.compose.material3.Text("Ripristino completato") },
                            text = { androidx.compose.material3.Text(report) }, confirmButton = {
                                androidx.compose.material3.TextButton(onClick = { backupReport = null }) { androidx.compose.material3.Text("Continua") }
                            })
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        backupReport?.let { outState.putString("backup-report", it) }
        super.onSaveInstanceState(outState)
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
