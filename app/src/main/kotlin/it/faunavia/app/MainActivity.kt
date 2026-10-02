package it.faunavia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val services = application as FaunaviaApplication
            CompositionLocalProvider(LocalSpeciesMetadata provides services.speciesMetadata,
                LocalSpeciesImageLoader provides services.speciesImageLoader) {
                FaunaviaTheme {
                    FaunaviaApp()
                }
            }
        }
    }
}
