package it.faunavia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/** Debug-only host restores the same injected content after a real Activity recreation. */
class AnalysisTestActivity : ComponentActivity() {
    companion object { var testContent: (@Composable () -> Unit)? = null }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        testContent?.let(::showTestContent)
    }
    fun showTestContent(content: @Composable () -> Unit) {
        testContent = content
        setContent { content() }
    }
}
