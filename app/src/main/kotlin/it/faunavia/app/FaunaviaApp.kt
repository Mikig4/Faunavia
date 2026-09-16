package it.faunavia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

enum class AppDestination(
    val route: String,
    val label: String,
    val description: String,
) {
    HOME("home", "Home", "Punto di partenza per posizione e itinerari."),
    ROUTES("routes", "Percorsi", "Importazione GPX e GeoJSON arriverà in F5."),
    RESULTS("results", "Risultati", "Evidenze documentate e plausibili resteranno separate."),
    DIARY("diary", "Diario", "Gli avvistamenti locali saranno disponibili offline."),
    CATALOGUE("catalogue", "Catalogo", "Ricerca di taxa Animalia accettati prevista in F3."),
    SETTINGS("settings", "Impostazioni", "Preferenze locali, cache e notifiche."),
}

private val FaunaviaBackground = Color(0xFFF4F7F2)
private val FaunaviaGreen = Color(0xFF1F5C3F)
private val FaunaviaNavigation = Color(0xFFE4EEE5)

@Composable
fun FaunaviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FaunaviaGreen,
            onPrimary = Color.White,
            background = FaunaviaBackground,
            surface = FaunaviaBackground,
            surfaceVariant = FaunaviaNavigation,
        ),
        content = content,
    )
}

@Composable
fun FaunaviaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(FaunaviaBackground)
            .testTag("app-root"),
        bottomBar = {
            DestinationBar(
                selectedRoute = currentDestination?.route,
                onDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.HOME.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
        containerColor = FaunaviaBackground,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            AppDestination.entries.forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(destination)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(destination: AppDestination) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FaunaviaBackground)
            .testTag("screen-${destination.route}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(FaunaviaGreen)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = "Faunavia",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = destination.label,
                    modifier = Modifier.testTag("screen-title-${destination.route}"),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = destination.description,
                color = Color(0xFF26382E),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Placeholder F1 — nessuna rete o dato personale richiesto.",
                color = Color(0xFF52675A),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DestinationBar(
    selectedRoute: String?,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(FaunaviaNavigation)
            .testTag("destination-bar"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(AppDestination.entries, key = { it.route }) { destination ->
            val selected = currentRouteMatches(selectedRoute, destination)
            Button(
                onClick = { onDestinationSelected(destination) },
                modifier = Modifier.testTag("nav-${destination.route}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) FaunaviaGreen else Color.White,
                    contentColor = if (selected) Color.White else FaunaviaGreen,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(destination.label)
                }
            }
        }
    }
}

internal fun currentRouteMatches(
    selectedRoute: String?,
    destination: AppDestination,
): Boolean = selectedRoute == destination.route
