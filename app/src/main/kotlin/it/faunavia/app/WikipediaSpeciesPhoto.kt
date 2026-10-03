package it.faunavia.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import it.faunavia.exploration.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WikipediaSpeciesPhoto(id: String, scientificName: String) {
    val lookup = LocalSpeciesMetadata.current
    val loader = LocalSpeciesImageLoader.current
    var retry by rememberSaveable(id, scientificName) { mutableIntStateOf(0) }
    var result by remember(id, scientificName) { mutableStateOf<MetadataResult<SpeciesPhoto>?>(null) }
    var bitmap by remember(id, scientificName) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(id, scientificName) { mutableStateOf(true) }
    var imageFailed by remember(id, scientificName) { mutableStateOf(false) }
    LaunchedEffect(id, scientificName, lookup, loader, retry) {
        loading = true; imageFailed = false
        try {
            val metadata = withContext(Dispatchers.IO) { lookup.photo(id, scientificName, retry > 0) }
            result = metadata
            bitmap = null
            metadata.value?.let { image ->
                try { bitmap = withContext(Dispatchers.IO) { loader.load(image.imageUrl) }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { imageFailed = true }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { result = MetadataResult(null, "Foto Wikipedia non recuperabile. Riprova.")
        } finally { loading = false }
    }
    val photo = result?.value
    val image = bitmap
    if (image != null && photo != null) {
        Image(image.asImageBitmap(), "Immagine Wikipedia di $scientificName", Modifier.fillMaxWidth().height(220.dp)
            .testTag("profile-wikipedia-image"), contentScale = ContentScale.Fit)
        Text("${photo.source.attribution} · ${photo.source.license}", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("profile-photo-credits"))
    } else SpeciesFallback()
    if (loading) Text("Caricamento foto Wikipedia…", modifier = Modifier.testTag("profile-photo-loading"))
    result?.warning?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile-photo-warning")) }
    if (result?.stale == true) Text("Foto da cache non aggiornata · consultata ${photo?.source?.retrievedAt}",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-photo-stale"))
    if (imageFailed) Text("Non riesco a caricare la foto. Il simbolo e i dati della scheda restano disponibili.",
        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile-photo-error"))
    if (photo != null) {
        ProfileSource("photo", photo.source, "Fonte e licenza della foto")
        val uri = LocalUriHandler.current
        val opener = LocalProfileSourceOpener.current
        var failed by remember(photo.articleUrl) { mutableStateOf(false) }
        TextButton(onClick = { failed = runCatching {
            if (opener != null) opener(photo.articleUrl) else uri.openUri(photo.articleUrl)
        }.isFailure }, modifier = Modifier.testTag("profile-wikipedia-link")) { Text("Apri voce Wikipedia") }
        if (failed) Text("Non riesco ad aprire Wikipedia. Puoi riprovare.", color = MaterialTheme.colorScheme.error)
    }
    if (!loading && (image == null || result?.warning != null)) OutlinedButton(onClick = { retry++ },
        modifier = Modifier.testTag("profile-photo-retry")) { Text("Riprova foto Wikipedia") }
}
