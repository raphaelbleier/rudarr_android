package uk.bleier.ruddarr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import uk.bleier.ruddarr.domain.HistoryItem
import uk.bleier.ruddarr.domain.InstanceConfig
import uk.bleier.ruddarr.domain.InstanceHeader
import uk.bleier.ruddarr.domain.InstanceMode
import uk.bleier.ruddarr.domain.MediaRecord
import uk.bleier.ruddarr.domain.QueueItem
import uk.bleier.ruddarr.domain.SeasonRecord
import uk.bleier.ruddarr.domain.ServiceMetadata
import uk.bleier.ruddarr.domain.ServiceType
import uk.bleier.ruddarr.domain.formatBytes
import uk.bleier.ruddarr.domain.toDisplayDate
import uk.bleier.ruddarr.ui.RuddarrBackdrop
import uk.bleier.ruddarr.ui.RuddarrTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<RuddarrViewModel>()
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refreshForDestination()
        else viewModel.showLocalNetworkPermissionRequired()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        setContent { RuddarrApp(viewModel) }
        requestLocalNetworkAccess()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val destination = when (intent.data?.host) {
            "movies" -> AppDestination.MOVIES
            "series" -> AppDestination.SERIES
            "calendar" -> AppDestination.CALENDAR
            "activity" -> AppDestination.ACTIVITY
            "settings" -> AppDestination.SETTINGS
            else -> null
        }
        destination?.let(viewModel::setDestination)
    }

    private fun requestLocalNetworkAccess() {
        if (
            Build.VERSION.SDK_INT < 37 ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.refreshForDestination()
        } else {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}

private data class DestinationSpec(
    val destination: AppDestination,
    val label: String,
    val icon: @Composable () -> Unit,
)

private val destinations = listOf(
    DestinationSpec(AppDestination.MOVIES, "Movies") { Icon(Icons.Default.Movie, null) },
    DestinationSpec(AppDestination.SERIES, "Series") { Icon(Icons.Default.Tv, null) },
    DestinationSpec(AppDestination.CALENDAR, "Calendar") { Icon(Icons.Default.CalendarMonth, null) },
    DestinationSpec(AppDestination.ACTIVITY, "Activity") { Icon(Icons.AutoMirrored.Filled.List, null) },
    DestinationSpec(AppDestination.SETTINGS, "Settings") { Icon(Icons.Default.Settings, null) },
)

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun RuddarrApp(viewModel: RuddarrViewModel) {
    val state = viewModel.state
    val snackbars = remember { SnackbarHostState() }
    val width = LocalActivity.current?.let { calculateWindowSizeClass(it).widthSizeClass }
        ?: WindowWidthSizeClass.Compact

    RuddarrTheme(darkTheme = true, useDynamicColor = false) {
        RuddarrBackdrop {
            LaunchedEffect(state.message) {
                state.message?.let {
                    snackbars.showSnackbar(it)
                    viewModel.consumeMessage()
                }
            }
            val content: @Composable (Modifier) -> Unit = { modifier ->
                AppContent(modifier, state, viewModel)
            }
            if (width == WindowWidthSizeClass.Compact) {
                Scaffold(
                    topBar = { AppBar(state, viewModel) },
                    bottomBar = { BottomNavigation(state, viewModel) },
                    snackbarHost = { SnackbarHost(snackbars) },
                    containerColor = Color.Transparent,
                ) { padding -> content(Modifier.padding(padding)) }
            } else {
                Scaffold(
                    topBar = { AppBar(state, viewModel) },
                    snackbarHost = { SnackbarHost(snackbars) },
                    containerColor = Color.Transparent,
                ) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        NavigationRail(containerColor = Color.Transparent) {
                            destinations.forEach { spec ->
                                NavigationRailItem(
                                    selected = state.destination == spec.destination,
                                    onClick = { viewModel.setDestination(spec.destination) },
                                    icon = spec.icon,
                                    label = { Text(spec.label) },
                                )
                            }
                        }
                        content(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppBar(state: AppState, viewModel: RuddarrViewModel) {
    TopAppBar(
        title = {
            Column {
                Text("Ruddarr", fontWeight = FontWeight.SemiBold)
                Text(state.destination.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
            }
        },
        actions = {
            if (state.isLoading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.padding(12.dp).size(22.dp),
                )
            }
            IconButton(onClick = viewModel::refreshForDestination) { Icon(Icons.Default.Refresh, "Refresh") }
        },
    )
}

@Composable
private fun BottomNavigation(state: AppState, viewModel: RuddarrViewModel) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) {
        destinations.forEach { spec ->
            NavigationBarItem(
                selected = state.destination == spec.destination,
                onClick = { viewModel.setDestination(spec.destination) },
                icon = spec.icon,
                label = { Text(spec.label) },
            )
        }
    }
}

@Composable
private fun AppContent(modifier: Modifier, state: AppState, viewModel: RuddarrViewModel) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state.destination,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(animationSpec = motion.defaultEffectsSpec()) +
                scaleIn(initialScale = 0.96f, animationSpec = motion.defaultSpatialSpec()))
                .togetherWith(
                    fadeOut(animationSpec = motion.fastEffectsSpec()) +
                        scaleOut(targetScale = 1.02f, animationSpec = motion.fastSpatialSpec()),
                )
        },
    ) { destination ->
        when (destination) {
            AppDestination.MOVIES -> MediaScreen(Modifier.fillMaxSize(), ServiceType.RADARR, state, viewModel)
            AppDestination.SERIES -> MediaScreen(Modifier.fillMaxSize(), ServiceType.SONARR, state, viewModel)
            AppDestination.CALENDAR -> CalendarScreen(Modifier.fillMaxSize(), state, viewModel)
            AppDestination.ACTIVITY -> ActivityScreen(Modifier.fillMaxSize(), state, viewModel)
            AppDestination.SETTINGS -> SettingsScreen(Modifier.fillMaxSize(), state, viewModel)
        }
    }
    state.selected?.let { record -> MediaSheet(record, state, viewModel) }
}

@Composable
private fun InstancePicker(service: ServiceType, state: AppState, viewModel: RuddarrViewModel) {
    val configured = state.instances.filter { it.service == service }
    val selected = viewModel.active(service)
    if (configured.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            configured.forEach { instance ->
                FilterChip(
                    selected = selected?.id == instance.id,
                    onClick = { viewModel.selectInstance(instance.id) },
                    label = { Text(instance.displayName()) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryModeButtonGroup(
    service: ServiceType,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val options = if (service == ServiceType.RADARR) {
        listOf("all" to "Library", "wanted" to "Wanted", "missing" to "Missing")
    } else {
        listOf("all" to "Library", "continuing" to "Continuing", "missing" to "Missing")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelected(value) },
                modifier = Modifier.weight(if (index == 1) 1.15f else 1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun LibraryHero(service: ServiceType, count: Int, instanceName: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (service == ServiceType.RADARR) "Movie library" else "Series library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$count ${if (service == ServiceType.RADARR) "titles" else "series"} in $instanceName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (service == ServiceType.RADARR) Icons.Default.Movie else Icons.Default.Tv,
                    contentDescription = null,
                    tint = colors.onTertiaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaScreen(modifier: Modifier, service: ServiceType, state: AppState, viewModel: RuddarrViewModel) {
    var query by remember(service) { mutableStateOf("") }
    var filter by remember(service) { mutableStateOf("all") }
    var sort by remember(service) { mutableStateOf("added") }
    var ascending by remember(service) { mutableStateOf(false) }
    var rootFolder by remember(service) { mutableStateOf<String?>(null) }
    val library = if (service == ServiceType.RADARR) state.movies else state.series
    val results = if (service == ServiceType.RADARR) state.movieSearch else state.seriesSearch
    val source = if (query.isBlank() || results.isNotEmpty()) if (results.isNotEmpty()) results else library else library.filter { it.title.contains(query, true) }
    val filtered = source.filterAndSort(service, filter, sort, ascending, rootFolder)
    val rootFolders = library.map { it.raw.optString("rootFolderPath") }.filter { it.isNotBlank() }.distinct().sorted()
    val instance = viewModel.active(service)

    if (instance == null) {
        EmptyState(modifier, "Add a ${service.apiLabel} instance", "Connect Ruddarr to begin managing your library.", "Open Settings") { viewModel.setDestination(AppDestination.SETTINGS) }
        return
    }
    Column(modifier.padding(horizontal = 16.dp)) {
        LibraryHero(service, filtered.size, instance.displayName())
        Spacer(Modifier.height(12.dp))
        InstancePicker(service, state, viewModel)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.length >= 2) viewModel.search(service, it) else viewModel.clearSearch(service)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) IconButton(onClick = { query = ""; viewModel.clearSearch(service) }) { Icon(Icons.Default.Close, "Clear") }
            },
            label = { Text("Search ${service.apiLabel}") },
        )
        Spacer(Modifier.height(10.dp))
        LibraryModeButtonGroup(service, filter) { filter = it }
        Spacer(Modifier.height(8.dp))
        if (service == ServiceType.RADARR) {
            FilterRow {
                listOf("monitored" to "Monitored", "unmonitored" to "Unmonitored", "downloaded" to "Downloaded", "dangling" to "Dangling").forEach { (value, label) ->
                    FilterChip(filter == value, { filter = value }, label = { Text(label) })
                }
            }
        } else {
            FilterRow {
                listOf("monitored" to "Monitored", "unmonitored" to "Unmonitored", "ended" to "Ended", "dangling" to "Dangling").forEach { (value, label) ->
                    FilterChip(filter == value, { filter = value }, label = { Text(label) })
                }
            }
        }
        FilterRow {
            listOf("title" to "Title", "year" to "Year", "added" to "Added", "rating" to "Rating", "size" to "Size").forEach { (value, label) ->
                FilterChip(sort == value, { sort = value; if (value == "title") ascending = true }, label = { Text(label) })
            }
        }
        FilterRow {
            FilterChip(ascending, { ascending = !ascending }, label = { Text(if (ascending) "Ascending" else "Descending") })
            if (rootFolders.isNotEmpty()) {
                FilterChip(rootFolder == null, { rootFolder = null }, label = { Text("All folders") })
                rootFolders.forEach { folder -> FilterChip(rootFolder == folder, { rootFolder = folder }, label = { Text(folder.substringAfterLast('/').ifBlank { folder }) }) }
            }
        }
        Text("${filtered.size} ${if (service == ServiceType.RADARR) "movies" else "series"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty() && !state.isLoading) {
            EmptyState(Modifier.fillMaxSize(), "No ${if (service == ServiceType.RADARR) "movies" else "series"}", "Search to add one or refresh this library.", "Refresh") { viewModel.refreshLibrary(service) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(156.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { "${service.name}-${it.id}-${it.title}" }) { record -> MediaCard(record, record.posterUrl(instance)) { if (record.raw.has("id")) viewModel.open(record) else viewModel.preview(record) }
                }
            }
        }
    }
}

private fun List<MediaRecord>.filterAndSort(
    service: ServiceType,
    filterValue: String,
    sort: String,
    ascending: Boolean,
    rootFolder: String?,
): List<MediaRecord> {
    val filtered = filter { record ->
        if (rootFolder != null && record.raw.optString("rootFolderPath") != rootFolder) return@filter false
        when (filterValue) {
            "all" -> true
            "monitored" -> record.monitored
            "unmonitored" -> !record.monitored
            "downloaded" -> record.hasFile
            "wanted" -> service == ServiceType.RADARR && record.monitored && !record.hasFile
            "missing" -> when (service) {
                ServiceType.RADARR -> record.monitored && !record.hasFile && record.raw.optBoolean("isAvailable", true)
                ServiceType.SONARR -> record.raw.optJSONObject("statistics")?.let { statistics ->
                    statistics.optInt("episodeCount") > statistics.optInt("episodeFileCount")
                } == true
            }
            "dangling" -> when (service) {
                ServiceType.RADARR -> !record.monitored && !record.hasFile
                ServiceType.SONARR -> !record.monitored && record.raw.optJSONObject("statistics")?.optInt("episodeCount") == 0
            }
            "continuing" -> service == ServiceType.SONARR && record.status.equals("continuing", ignoreCase = true)
            "ended" -> service == ServiceType.SONARR && record.status.equals("ended", ignoreCase = true)
            else -> true
        }
    }
    val sorted = when (sort) {
        "title" -> filtered.sortedBy { it.title.lowercase() }
        "year" -> filtered.sortedBy { it.year }
        "rating" -> filtered.sortedBy { it.raw.optJSONObject("ratings")?.optDouble("value") ?: 0.0 }
        "size" -> filtered.sortedBy {
            if (service == ServiceType.RADARR) it.raw.optLong("sizeOnDisk") else it.raw.optJSONObject("statistics")?.optLong("sizeOnDisk") ?: 0L
        }
        else -> filtered.sortedBy { it.raw.optString("added") }
    }
    return if (ascending) sorted else sorted.reversed()
}

@Composable
private fun MediaCard(record: MediaRecord, posterUrl: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val posterBase = if (record.service == ServiceType.RADARR) colors.primaryContainer else colors.secondaryContainer
    val posterAccent = if (record.service == ServiceType.RADARR) colors.tertiaryContainer else colors.primaryContainer
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.68f)),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(212.dp)
                    .background(Brush.linearGradient(listOf(posterBase, posterAccent))),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        record.title.take(1).uppercase(),
                        style = MaterialTheme.typography.displayLarge,
                        color = if (record.service == ServiceType.RADARR) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        fontWeight = FontWeight.Black,
                    )
                    record.year.takeIf { it > 0 }?.let { year ->
                        Text(
                            year.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (record.service == ServiceType.RADARR) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        )
                    }
                }
                Icon(
                    if (record.service == ServiceType.RADARR) Icons.Default.Movie else Icons.Default.Tv,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(22.dp),
                    tint = if (record.service == ServiceType.RADARR) colors.onPrimaryContainer.copy(alpha = 0.72f) else colors.onSecondaryContainer.copy(alpha = 0.72f),
                )
                AsyncImage(
                    model = posterUrl,
                    contentDescription = "Poster for ${record.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(record.details.ifBlank { record.status }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(record.stateLabel, style = MaterialTheme.typography.labelSmall, color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaSheet(record: MediaRecord, state: AppState, viewModel: RuddarrViewModel) {
    val instance = viewModel.instanceFor(record)
    var editing by remember(record.id, record.service) { mutableStateOf(false) }
    var deleteMovieFileConfirmation by remember(record.id, record.service) { mutableStateOf(false) }
    var expandedSeasons by remember(record.id, record.instanceId) { mutableStateOf(emptySet<Int>()) }
    var deleteSeasonConfirmation by remember(record.id, record.instanceId) { mutableStateOf<SeasonRecord?>(null) }
    ModalBottomSheet(onDismissRequest = viewModel::closeDetails) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(record.posterUrl(instance), "Poster for ${record.title}", Modifier.size(width = 92.dp, height = 138.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(record.details.ifBlank { record.status }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item { Text(record.overview.ifBlank { "No overview supplied by ${record.service.apiLabel}." }, style = MaterialTheme.typography.bodyMedium) }
            if (record.raw.has("id")) item { MediaInformationCard(record, state.metadata[instance?.id]) }
            item {
                if (!record.raw.has("id")) {
                    AddMediaActions(record, state.metadata[instance?.id], viewModel)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterRow {
                            Button(onClick = { viewModel.setMonitored(record, !record.monitored) }) { Text(if (record.monitored) "Unmonitor" else "Monitor") }
                            OutlinedButton(onClick = { viewModel.searchMedia(record) }) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Automatic") }
                            if (record.service == ServiceType.RADARR) OutlinedButton(onClick = { viewModel.lookupReleases(record) }) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Releases") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.loadMediaHistory(record) }) { Icon(Icons.AutoMirrored.Filled.List, null); Spacer(Modifier.width(6.dp)); Text("Activity") }
                            OutlinedButton(onClick = { editing = !editing }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text(if (editing) "Close edit" else "Edit") }
                        }
                    }
                }
            }
            if (record.service == ServiceType.RADARR && record.hasFile) item {
                MovieFileCard(record, onDelete = { deleteMovieFileConfirmation = true })
            }
            if (editing && record.raw.has("id")) {
                item { MediaEditForm(record, state.metadata[instance?.id], viewModel) }
            }
            if (record.raw.has("id")) item { DestructiveMediaActions(record, viewModel) }
            if (record.service == ServiceType.SONARR && record.raw.has("id")) {
                item { HorizontalDivider() }
                if (record.seasons.isNotEmpty()) {
                    item { Text("Seasons", style = MaterialTheme.typography.titleMedium) }
                    items(record.seasons, key = { it.number }) { season ->
                        val seasonEpisodes = state.episodes.filter { it.seasonNumber == season.number }.sortedByDescending { it.episodeNumber }
                        SeasonCard(
                            season = season,
                            episodes = seasonEpisodes,
                            expanded = expandedSeasons.contains(season.number),
                            onToggleExpanded = {
                                expandedSeasons = if (expandedSeasons.contains(season.number)) expandedSeasons - season.number else expandedSeasons + season.number
                            },
                            onSetMonitored = { viewModel.setSeasonMonitored(record, season.number, !season.monitored) },
                            onAutomaticSearch = { viewModel.searchSeason(record, season.number) },
                            onReleases = { viewModel.lookupReleases(record, season.number) },
                            onDeleteFiles = { deleteSeasonConfirmation = season },
                        )
                        if (expandedSeasons.contains(season.number)) {
                            seasonEpisodes.forEach { episode -> EpisodeRow(episode, record, viewModel) }
                            if (seasonEpisodes.isEmpty()) Text("No episodes returned for this season.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    item { Text("Episodes", style = MaterialTheme.typography.titleMedium) }
                    items(state.episodes, key = { it.id }) { episode -> EpisodeRow(episode, record, viewModel) }
                }
                if (state.episodeHistoryForId != null) {
                    item { Text("Episode activity", style = MaterialTheme.typography.titleMedium) }
                    items(state.episodeHistory, key = { "${it.raw.optInt("id")}-${it.raw.optString("date")}" }) { event ->
                        Text("${event.eventType} · ${event.title}", style = MaterialTheme.typography.bodyMedium)
                        Text(event.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.episodeHistory.isEmpty()) item { Text("No activity for this episode.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (state.mediaHistoryForId == record.id) {
                item { HorizontalDivider() }
                item { Text("Activity", style = MaterialTheme.typography.titleMedium) }
                if (state.mediaHistory.isEmpty()) item { Text("No activity for this item.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.mediaHistory, key = { "${it.raw.optInt("id")}-${it.raw.optString("date")}" }) { event ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(event.eventType, style = MaterialTheme.typography.titleSmall)
                            Text(event.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.releases.isNotEmpty()) {
                item { HorizontalDivider() }
                item { ReleaseResults(record, state.releases, viewModel) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    if (deleteMovieFileConfirmation) AlertDialog(
        onDismissRequest = { deleteMovieFileConfirmation = false },
        title = { Text("Delete movie file?") },
        text = { Text("The movie stays in Radarr and can be downloaded again while monitored.") },
        confirmButton = { TextButton(onClick = { deleteMovieFileConfirmation = false; viewModel.deleteMovieFile(record) }) { Text("Delete file") } },
        dismissButton = { TextButton(onClick = { deleteMovieFileConfirmation = false }) { Text("Cancel") } },
    )
    deleteSeasonConfirmation?.let { season ->
        AlertDialog(
            onDismissRequest = { deleteSeasonConfirmation = null },
            title = { Text("Delete files for ${season.label}?") },
            text = { Text("All downloaded episode files in this season are deleted and those episodes are unmonitored.") },
            confirmButton = { TextButton(onClick = { deleteSeasonConfirmation = null; viewModel.deleteSeasonFiles(record, season.number) }) { Text("Delete files") } },
            dismissButton = { TextButton(onClick = { deleteSeasonConfirmation = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MovieFileCard(record: MediaRecord, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Movie file", style = MaterialTheme.typography.titleMedium)
            record.filePath.takeIf { it.isNotBlank() }?.let { path -> Text(path, style = MaterialTheme.typography.bodySmall) }
            listOf(record.fileQuality, record.fileSize.takeIf { it > 0 }?.formatBytes()).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }?.let { details ->
                Text(details, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete file") }
        }
    }
}

@Composable
private fun MediaInformationCard(record: MediaRecord, metadata: ServiceMetadata?) {
    val tagNames = record.raw.optJSONArray("tags").toIntSet().map { id ->
        metadata?.tags?.firstOrNull { it.id == id }?.label ?: id.toString()
    }
    val profile = record.raw.optInt("qualityProfileId").takeIf { it > 0 }?.let { id ->
        metadata?.qualityProfiles?.firstOrNull { it.id == id }?.label ?: "Profile $id"
    }
    val details = buildList {
        profile?.let { add("Quality profile" to it) }
        record.raw.optString("rootFolderPath").takeIf { it.isNotBlank() }?.let { add("Root folder" to it) }
        tagNames.takeIf { it.isNotEmpty() }?.let { add("Tags" to it.joinToString(", ")) }
        if (record.service == ServiceType.RADARR) {
            record.raw.optString("minimumAvailability").takeIf { it.isNotBlank() }?.let { add("Minimum availability" to it.replaceFirstChar { character -> character.uppercase() }) }
            listOf("inCinemas" to "In cinemas", "digitalRelease" to "Digital release", "physicalRelease" to "Physical release").forEach { (key, label) ->
                record.raw.optString(key).takeIf { it.isNotBlank() }?.let { add(label to it.toDisplayDate()) }
            }
        } else {
            record.raw.optString("seriesType").takeIf { it.isNotBlank() }?.let { add("Series type" to it.replaceFirstChar { character -> character.uppercase() }) }
            record.raw.optString("monitorNewItems").takeIf { it.isNotBlank() }?.let { add("New seasons" to if (it == "all") "Monitored" else "Unmonitored") }
            if (record.raw.has("seasonFolder")) add("Season folders" to if (record.raw.optBoolean("seasonFolder")) "Yes" else "No")
        }
        val ratings = record.raw.optJSONObject("ratings")
        listOf("imdb" to "IMDb", "tmdb" to "TMDb", "rottenTomatoes" to "Rotten Tomatoes", "metacritic" to "Metacritic", "trakt" to "Trakt").forEach { (key, label) ->
            ratings?.optJSONObject(key)?.optDouble("value")?.takeIf { it > 0 }?.let { value ->
                add(label to if (key == "rottenTomatoes") "${value.toInt()}%" else value.toString())
            }
        }
    }
    if (details.isNotEmpty()) Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Information", style = MaterialTheme.typography.titleMedium)
            details.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, Modifier.weight(0.42f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ReleaseResults(record: MediaRecord, releases: List<org.json.JSONObject>, viewModel: RuddarrViewModel) {
    var query by remember(record.id, releases.size) { mutableStateOf("") }
    var indexer by remember(record.id, releases.size) { mutableStateOf<String?>(null) }
    var quality by remember(record.id, releases.size) { mutableStateOf<String?>(null) }
    var protocol by remember(record.id, releases.size) { mutableStateOf<String?>(null) }
    var language by remember(record.id, releases.size) { mutableStateOf<String?>(null) }
    var customFormat by remember(record.id, releases.size) { mutableStateOf<String?>(null) }
    var approvedOnly by remember(record.id, releases.size) { mutableStateOf(false) }
    var seasonPack by remember(record.id, releases.size) { mutableStateOf<String?>(null) }
    var sortBy by remember(record.id, releases.size) { mutableStateOf("Weight") }
    var ascending by remember(record.id, releases.size) { mutableStateOf(false) }
    val indexers = releases.map { it.optString("indexer") }.filter { it.isNotBlank() }.distinct().sorted()
    val qualities = releases.map { it.releaseQuality() }.filter { it.isNotBlank() }.distinct().sorted()
    val protocols = releases.map { it.optString("protocol") }.filter { it.isNotBlank() }.distinct().sorted()
    val languages = releases.flatMap { it.releaseLanguages() }.distinct().sorted()
    val customFormats = releases.flatMap { it.releaseCustomFormats() }.distinct().sorted()
    val filtered = releases.filter { release ->
        (query.isBlank() || release.optString("title").contains(query, ignoreCase = true)) &&
            (indexer == null || release.optString("indexer") == indexer) &&
            (quality == null || release.releaseQuality() == quality) &&
            (protocol == null || release.optString("protocol").equals(protocol, ignoreCase = true)) &&
            (language == null || release.releaseLanguages().contains(language)) &&
            (customFormat == null || release.releaseCustomFormats().contains(customFormat)) &&
            (!approvedOnly || !release.optBoolean("rejected")) &&
            (seasonPack == null || if (seasonPack == "Season pack") release.optBoolean("fullSeason") else !release.optBoolean("fullSeason"))
    }.sortedBy { release ->
        when (sortBy) {
            "Age" -> release.optDouble("ageMinutes")
            "Quality" -> release.optJSONObject("quality")?.optJSONObject("quality")?.optDouble("resolution") ?: 0.0
            "Size" -> release.optDouble("size")
            "Custom score" -> release.optDouble("customFormatScore")
            else -> release.optDouble("releaseWeight")
        }
    }.let { values -> if (ascending) values else values.reversed() }
    val hasFilters = query.isNotBlank() || indexer != null || quality != null || protocol != null || language != null || customFormat != null || approvedOnly || seasonPack != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Available releases", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Filter releases") }, singleLine = true)
        FilterRow {
            listOf("Weight", "Age", "Quality", "Size", "Custom score").forEach { option -> FilterChip(sortBy == option, { sortBy = option }, label = { Text(option) }) }
            FilterChip(ascending, { ascending = !ascending }, label = { Text(if (ascending) "Ascending" else "Descending") })
        }
        if (protocols.size > 1) FilterRow {
            FilterChip(protocol == null, { protocol = null }, label = { Text("All protocols") })
            protocols.forEach { option -> FilterChip(protocol == option, { protocol = option }, label = { Text(option.replaceFirstChar { it.uppercase() }) }) }
        }
        if (indexers.isNotEmpty()) FilterRow {
            FilterChip(indexer == null, { indexer = null }, label = { Text("All indexers") })
            indexers.forEach { option -> FilterChip(indexer == option, { indexer = option }, label = { Text(option) }) }
        }
        if (qualities.isNotEmpty()) FilterRow {
            FilterChip(quality == null, { quality = null }, label = { Text("All qualities") })
            qualities.forEach { option -> FilterChip(quality == option, { quality = option }, label = { Text(option) }) }
        }
        if (languages.isNotEmpty()) FilterRow {
            FilterChip(language == null, { language = null }, label = { Text("All languages") })
            languages.forEach { option -> FilterChip(language == option, { language = option }, label = { Text(option) }) }
        }
        if (customFormats.isNotEmpty()) FilterRow {
            FilterChip(customFormat == null, { customFormat = null }, label = { Text("All formats") })
            customFormats.forEach { option -> FilterChip(customFormat == option, { customFormat = option }, label = { Text(option) }) }
        }
        FilterRow {
            FilterChip(approvedOnly, { approvedOnly = !approvedOnly }, label = { Text("Approved only") })
            if (record.service == ServiceType.SONARR) {
                FilterChip(seasonPack == null, { seasonPack = null }, label = { Text("Any pack") })
                FilterChip(seasonPack == "Season pack", { seasonPack = "Season pack" }, label = { Text("Season pack") })
                FilterChip(seasonPack == "Episode", { seasonPack = "Episode" }, label = { Text("Episode") })
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(Modifier.fillMaxWidth().padding(vertical = 16.dp), "No matching releases", "Adjust the local filters or search.", if (hasFilters) "Clear filters" else null) {
                query = ""; indexer = null; quality = null; protocol = null; language = null; customFormat = null; approvedOnly = false; seasonPack = null
            }
        }
        filtered.forEach { release ->
            val rejected = release.optBoolean("rejected")
            val canDownload = release.optBoolean("downloadAllowed", true) && !rejected
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(release.optString("title", "Release"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(release.summary(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    release.releaseLanguages().takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (rejected) Text(release.rejections().ifEmpty { listOf("Rejected by ${record.service.apiLabel}") }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { viewModel.download(record, release) }, enabled = canDownload) { Icon(Icons.Default.Download, "Send to download client") }
            }
        }
    }
}

@Composable
private fun MediaEditForm(record: MediaRecord, metadata: ServiceMetadata?, viewModel: RuddarrViewModel) {
    var rootFolder by remember(record.id) { mutableStateOf(record.raw.optString("rootFolderPath")) }
    var profileId by remember(record.id) { mutableStateOf(record.raw.optInt("qualityProfileId")) }
    var tags by remember(record.id) { mutableStateOf(record.raw.optJSONArray("tags").toIntSet()) }
    var minimumAvailability by remember(record.id) { mutableStateOf(record.raw.optString("minimumAvailability", "announced")) }
    var monitorNewItems by remember(record.id) { mutableStateOf(record.raw.optString("monitorNewItems", "none")) }
    var seriesType by remember(record.id) { mutableStateOf(record.raw.optString("seriesType", "standard")) }
    var seasonFolder by remember(record.id) { mutableStateOf(record.raw.optBoolean("seasonFolder", true)) }
    var moveFiles by remember(record.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Edit library settings", style = MaterialTheme.typography.titleMedium)
            Text("Root folder", style = MaterialTheme.typography.labelLarge)
            metadata?.rootFolders?.forEach { folder ->
                FilterChip(rootFolder == folder.label, { rootFolder = folder.label }, label = { Text(folder.label) })
            }
            if (metadata?.rootFolders.isNullOrEmpty()) OutlinedTextField(rootFolder, { rootFolder = it }, Modifier.fillMaxWidth(), label = { Text("Root folder") })
            Text("Quality profile", style = MaterialTheme.typography.labelLarge)
            metadata?.qualityProfiles?.forEach { profile ->
                FilterChip(profileId == profile.id, { profileId = profile.id }, label = { Text(profile.label) })
            }
            if (metadata?.qualityProfiles.isNullOrEmpty()) OutlinedTextField(profileId.takeIf { it > 0 }?.toString().orEmpty(), { profileId = it.toIntOrNull() ?: 0 }, Modifier.fillMaxWidth(), label = { Text("Quality profile ID") })
            if (!metadata?.tags.isNullOrEmpty()) {
                Text("Tags", style = MaterialTheme.typography.labelLarge)
                metadata.tags.forEach { tag ->
                    FilterChip(
                        selected = tags.contains(tag.id),
                        onClick = { tags = if (tags.contains(tag.id)) tags - tag.id else tags + tag.id },
                        label = { Text(tag.label) },
                    )
                }
            }
            if (record.service == ServiceType.RADARR) {
                Text("Minimum availability", style = MaterialTheme.typography.labelLarge)
                listOf("announced", "inCinemas", "released").forEach { availability ->
                    FilterChip(minimumAvailability == availability, { minimumAvailability = availability }, label = { Text(availability.replaceFirstChar { it.uppercase() }) })
                }
            } else {
                Text("Monitor new seasons", style = MaterialTheme.typography.labelLarge)
                FilterChip(monitorNewItems == "all", { monitorNewItems = "all" }, label = { Text("All") })
                FilterChip(monitorNewItems != "all", { monitorNewItems = "none" }, label = { Text("None") })
                Text("Series type", style = MaterialTheme.typography.labelLarge)
                listOf("standard" to "Standard", "daily" to "Daily", "anime" to "Anime").forEach { (value, label) ->
                    FilterChip(seriesType == value, { seriesType = value }, label = { Text(label) })
                }
                Text("Season folders", style = MaterialTheme.typography.labelLarge)
                FilterChip(seasonFolder, { seasonFolder = true }, label = { Text("On") })
                FilterChip(!seasonFolder, { seasonFolder = false }, label = { Text("Off") })
            }
            Text("Move files when changing the root folder", style = MaterialTheme.typography.labelLarge)
            FilterChip(moveFiles, { moveFiles = !moveFiles }, label = { Text(if (moveFiles) "Move files" else "Keep files in place") })
            Button(
                onClick = {
                    viewModel.saveMediaEdits(
                        record = record,
                        rootFolder = rootFolder,
                        profileId = profileId,
                        tagIds = tags,
                        minimumAvailability = minimumAvailability.takeIf { record.service == ServiceType.RADARR },
                        monitorNewItems = monitorNewItems.takeIf { record.service == ServiceType.SONARR },
                        seriesType = seriesType.takeIf { record.service == ServiceType.SONARR },
                        seasonFolder = seasonFolder.takeIf { record.service == ServiceType.SONARR },
                        moveFiles = moveFiles,
                    )
                },
                enabled = rootFolder.isNotBlank() && profileId > 0,
            ) { Text("Save library settings") }
        }
    }
}

@Composable
private fun SeasonCard(
    season: SeasonRecord,
    episodes: List<uk.bleier.ruddarr.domain.EpisodeRecord>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetMonitored: () -> Unit,
    onAutomaticSearch: () -> Unit,
    onReleases: () -> Unit,
    onDeleteFiles: () -> Unit,
) {
    val downloaded = episodes.count { it.hasFile }
    val total = season.totalEpisodeCount.takeIf { it > 0 } ?: episodes.size
    val details = buildList {
        if (total > 0) add("$downloaded / $total downloaded")
        season.sizeOnDisk.takeIf { it > 0 }?.let { add(it.formatBytes()) }
    }.joinToString(" · ")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(season.label, style = MaterialTheme.typography.titleMedium)
                    if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSetMonitored) { Icon(if (season.monitored) Icons.Default.CheckCircle else Icons.Default.Close, if (season.monitored) "Unmonitor ${season.label}" else "Monitor ${season.label}") }
            }
            FilterRow {
                AssistChip(onClick = onToggleExpanded, label = { Text(if (expanded) "Hide episodes" else "Episodes") })
                AssistChip(onClick = onAutomaticSearch, label = { Text("Automatic") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                AssistChip(onClick = onReleases, label = { Text("Releases") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                if (episodes.any { it.hasFile }) AssistChip(onClick = onDeleteFiles, label = { Text("Delete files") }, leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: uk.bleier.ruddarr.domain.EpisodeRecord, series: MediaRecord, viewModel: RuddarrViewModel) {
    var deleteFileConfirmation by remember(episode.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${episode.code} · ${episode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(episode.airDate.takeIf { it.isNotBlank() }, if (episode.hasFile) "Downloaded" else if (episode.monitored) "Missing" else "Unmonitored").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { viewModel.setEpisodeMonitored(episode, !episode.monitored) }) {
            Icon(if (episode.monitored) Icons.Default.CheckCircle else Icons.Default.Close, if (episode.monitored) "Unmonitor episode" else "Monitor episode")
        }
        IconButton(onClick = { viewModel.searchEpisode(series, episode) }) {
            Icon(Icons.Default.Search, "Automatically search for this episode")
        }
        IconButton(onClick = { viewModel.lookupReleases(series, episodeId = episode.id) }) {
            Icon(Icons.Default.Search, "Search episode releases")
        }
        IconButton(onClick = { viewModel.loadEpisodeHistory(episode) }) {
            Icon(Icons.AutoMirrored.Filled.List, "Show episode activity")
        }
        if (episode.hasFile) {
            IconButton(onClick = { deleteFileConfirmation = true }) { Icon(Icons.Default.Delete, "Delete episode file") }
        }
    }
    if (deleteFileConfirmation) AlertDialog(
        onDismissRequest = { deleteFileConfirmation = false },
        title = { Text("Delete the file for ${episode.code}?") },
        text = { Text("The episode remains in Sonarr and can be downloaded again if it is monitored.") },
        confirmButton = { TextButton(onClick = { deleteFileConfirmation = false; viewModel.deleteEpisodeFile(episode) }) { Text("Delete file") } },
        dismissButton = { TextButton(onClick = { deleteFileConfirmation = false }) { Text("Cancel") } },
    )
}

@Composable
private fun AddMediaActions(record: MediaRecord, metadata: ServiceMetadata?, viewModel: RuddarrViewModel) {
    val rootFolders = metadata?.rootFolders.orEmpty()
    val qualityProfiles = metadata?.qualityProfiles.orEmpty()
    val availableTags = metadata?.tags.orEmpty()
    var root by remember(record.title) { mutableStateOf(metadata?.rootFolders?.firstOrNull()?.label.orEmpty()) }
    var profile by remember(record.title) { mutableStateOf(metadata?.qualityProfiles?.firstOrNull()?.id ?: 0) }
    var tags by remember(record.title) { mutableStateOf(emptySet<Int>()) }
    var movieMonitor by remember(record.title) { mutableStateOf("movieOnly") }
    var minimumAvailability by remember(record.title) { mutableStateOf("announced") }
    var seriesMonitor by remember(record.title) { mutableStateOf("none") }
    var seriesType by remember(record.title) { mutableStateOf("standard") }
    var seasonFolder by remember(record.title) { mutableStateOf(true) }
    Text("Add to ${record.service.apiLabel}", style = MaterialTheme.typography.titleMedium)
    Text("Root folder", style = MaterialTheme.typography.labelLarge)
    if (rootFolders.isEmpty()) {
        OutlinedTextField(root, { root = it }, Modifier.fillMaxWidth(), label = { Text("Root folder") }, supportingText = { Text("Choose a configured path from Settings metadata.") })
    } else {
        rootFolders.forEach { folder -> FilterChip(root == folder.label, { root = folder.label }, label = { Text(folder.label) }) }
    }
    Text("Quality profile", style = MaterialTheme.typography.labelLarge)
    if (qualityProfiles.isEmpty()) {
        OutlinedTextField(profile.takeIf { it > 0 }?.toString().orEmpty(), { profile = it.toIntOrNull() ?: 0 }, Modifier.fillMaxWidth(), label = { Text("Quality profile ID") })
    } else {
        qualityProfiles.forEach { quality -> FilterChip(profile == quality.id, { profile = quality.id }, label = { Text(quality.label) }) }
    }
    if (availableTags.isNotEmpty()) {
        Text("Tags", style = MaterialTheme.typography.labelLarge)
        availableTags.forEach { tag ->
            FilterChip(tags.contains(tag.id), { tags = if (tags.contains(tag.id)) tags - tag.id else tags + tag.id }, label = { Text(tag.label) })
        }
    }
    if (record.service == ServiceType.RADARR) {
        Text("Monitor", style = MaterialTheme.typography.labelLarge)
        listOf("movieOnly" to "Movie", "movieAndCollection" to "Movie + collection", "none" to "None").forEach { (value, label) ->
            FilterChip(movieMonitor == value, { movieMonitor = value }, label = { Text(label) })
        }
        Text("Minimum availability", style = MaterialTheme.typography.labelLarge)
        listOf("announced", "inCinemas", "released").forEach { availability ->
            FilterChip(minimumAvailability == availability, { minimumAvailability = availability }, label = { Text(availability.replaceFirstChar { it.uppercase() }) })
        }
    } else {
        Text("Monitor", style = MaterialTheme.typography.labelLarge)
        listOf(
            "all" to "All episodes",
            "future" to "Future episodes",
            "missing" to "Missing episodes",
            "existing" to "Existing episodes",
            "firstSeason" to "First season",
            "lastSeason" to "Last season",
            "pilot" to "Pilot",
            "recent" to "Recent episodes",
            "monitorSpecials" to "Monitor specials",
            "unmonitorSpecials" to "Unmonitor specials",
            "none" to "None",
        ).forEach { (value, label) -> FilterChip(seriesMonitor == value, { seriesMonitor = value }, label = { Text(label) }) }
        Text("Series type", style = MaterialTheme.typography.labelLarge)
        listOf("standard" to "Standard", "daily" to "Daily", "anime" to "Anime").forEach { (value, label) ->
            FilterChip(seriesType == value, { seriesType = value }, label = { Text(label) })
        }
        Text("Season folders", style = MaterialTheme.typography.labelLarge)
        FilterChip(seasonFolder, { seasonFolder = true }, label = { Text("On") })
        FilterChip(!seasonFolder, { seasonFolder = false }, label = { Text("Off") })
    }
    Button(
        onClick = {
            viewModel.add(
                record = record,
                rootFolder = root,
                profileId = profile,
                tagIds = tags,
                monitor = if (record.service == ServiceType.RADARR) movieMonitor else seriesMonitor,
                minimumAvailability = minimumAvailability.takeIf { record.service == ServiceType.RADARR },
                seriesType = seriesType.takeIf { record.service == ServiceType.SONARR },
                seasonFolder = seasonFolder,
            )
        },
        enabled = root.isNotBlank() && profile > 0,
    ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add") }
}

@Composable
private fun DestructiveMediaActions(record: MediaRecord, viewModel: RuddarrViewModel) {
    var confirm by remember { mutableStateOf(false) }
    var deleteFiles by remember { mutableStateOf(false) }
    var blocklist by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${record.title}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This removes the item from ${record.service.apiLabel}.")
                FilterChip(deleteFiles, { deleteFiles = !deleteFiles }, label = { Text(if (deleteFiles) "Delete files" else "Keep files") })
                FilterChip(blocklist, { blocklist = !blocklist }, label = { Text(if (blocklist) "Add exclusion" else "Do not add exclusion") })
            }
        },
        confirmButton = { TextButton(onClick = { confirm = false; viewModel.delete(record, deleteFiles, blocklist) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalendarRangeButtonGroup(days: Int, onDaysChanged: (Int) -> Unit) {
    val ranges = listOf(45, 90, 180)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ranges.forEachIndexed { index, range ->
            ToggleButton(
                checked = days == range,
                onCheckedChange = { onDaysChanged(range) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    ranges.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text("$range days")
            }
        }
    }
}

@Composable
private fun ReleaseHero(releaseCount: Int, days: Int) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Release radar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$releaseCount scheduled in the next $days days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = colors.onTertiaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalendarScreen(modifier: Modifier, state: AppState, viewModel: RuddarrViewModel) {
    var instanceFilter by remember { mutableStateOf<String?>(null) }
    var mediaFilter by remember { mutableStateOf<ServiceType?>(null) }
    var onlyMonitored by remember { mutableStateOf(false) }
    var onlyPremieres by remember { mutableStateOf(false) }
    var hideSpecials by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(45) }
    val records = state.calendar
        .filter { record -> instanceFilter == null || record.instanceId == instanceFilter }
        .filter { record -> mediaFilter == null || record.service == mediaFilter }
        .filter { record -> !onlyMonitored || record.monitored }
        .filter { record -> !onlyPremieres || record.isPremiere }
        .filter { record -> !hideSpecials || !record.isSpecial }
        .groupBy { record ->
            record.raw.optString("inCinemas", record.raw.optString("airDateUtc", record.raw.optString("airDate")))
                .take(10)
                .ifBlank { "Unscheduled" }
        }
        .toSortedMap()
    val releaseCount = records.values.sumOf { it.size }

    if (state.calendar.isEmpty() && !state.isLoading) EmptyState(modifier, "Nothing scheduled", "The next $days days are clear, or no instance is configured.", "Refresh") { viewModel.refreshCalendar(days) }
    else LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ReleaseHero(releaseCount, days) }
        item { CalendarRangeButtonGroup(days) { range -> days = range; viewModel.refreshCalendar(range) } }
        if (state.instances.size > 1) item {
            FilterRow {
                FilterChip(instanceFilter == null, { instanceFilter = null }, label = { Text("All instances") })
                state.instances.forEach { instance -> FilterChip(instanceFilter == instance.id, { instanceFilter = instance.id }, label = { Text(instance.displayName()) }) }
            }
        }
        item {
            FilterRow {
                FilterChip(mediaFilter == null, { mediaFilter = null }, label = { Text("All media") })
                FilterChip(mediaFilter == ServiceType.RADARR, { mediaFilter = ServiceType.RADARR }, label = { Text("Movies") })
                FilterChip(mediaFilter == ServiceType.SONARR, { mediaFilter = ServiceType.SONARR }, label = { Text("Series") })
            }
        }
        item {
            FilterRow {
                FilterChip(onlyMonitored, { onlyMonitored = !onlyMonitored }, label = { Text("Monitored") })
                FilterChip(onlyPremieres, { onlyPremieres = !onlyPremieres }, label = { Text("Premieres") })
                FilterChip(hideSpecials, { hideSpecials = !hideSpecials }, label = { Text("Hide specials") })
            }
        }
        if (records.isEmpty()) item { EmptyState(Modifier.fillMaxWidth().padding(vertical = 32.dp), "No matching releases", "Adjust the filters or extend the date range.", "Clear filters") { instanceFilter = null; mediaFilter = null; onlyMonitored = false; onlyPremieres = false; hideSpecials = false } }
        records.forEach { (date, itemsForDate) ->
            item {
                Text(
                    date.takeUnless { it == "Unscheduled" }?.toDisplayDate() ?: date,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(itemsForDate, key = { "${it.instanceId}-${it.service}-${it.id}-${it.title}-${it.calendarDate}" }) { record ->
                Card(
                    onClick = { if (record.raw.has("id")) viewModel.open(record) else viewModel.preview(record) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val posterBase = if (record.service == ServiceType.RADARR) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                        Box(
                            modifier = Modifier
                                .size(54.dp, 78.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(posterBase),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                record.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = if (record.service == ServiceType.RADARR) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                            )
                            AsyncImage(record.posterUrl(viewModel.instanceFor(record)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.title, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(record.stateLabel, record.details).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            record.service.apiLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueHistoryButtonGroup(showHistory: Boolean, onModeChanged: (Boolean) -> Unit) {
    val modes = listOf(false to "Queue", true to "History")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, (history, label) ->
            ToggleButton(
                checked = showHistory == history,
                onCheckedChange = { onModeChanged(history) },
                modifier = Modifier.weight(1f),
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ActivityHero(queueCount: Int, issueCount: Int, showingHistory: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = if (showingHistory) colors.primaryContainer else colors.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (showingHistory) "Recent activity" else "Download pulse",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (showingHistory) colors.onPrimaryContainer else colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (showingHistory) "Completed downloads and imports" else "$queueCount active downloads${if (issueCount > 0) ", $issueCount need attention" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (showingHistory) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (issueCount > 0 && !showingHistory) colors.secondaryContainer else colors.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (showingHistory) Icons.AutoMirrored.Filled.List else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (issueCount > 0 && !showingHistory) colors.onSecondaryContainer else colors.onTertiaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActivityScreen(modifier: Modifier, state: AppState, viewModel: RuddarrViewModel) {
    var showHistory by remember { mutableStateOf(false) }
    var selectedQueueItem by remember { mutableStateOf<QueueItem?>(null) }
    var instanceFilter by remember { mutableStateOf<String?>(null) }
    var protocolFilter by remember { mutableStateOf<String?>(null) }
    var eventFilter by remember { mutableStateOf<String?>(null) }
    var issuesOnly by remember { mutableStateOf(false) }
    var sortByTitle by remember { mutableStateOf(false) }
    var ascending by remember { mutableStateOf(false) }
    val queueItems = state.queue
        .filter { item -> instanceFilter == null || item.instance.id == instanceFilter }
        .filter { item -> protocolFilter == null || item.protocol.equals(protocolFilter, ignoreCase = true) }
        .filter { item -> !issuesOnly || item.isIssue }
        .sortedWith(if (sortByTitle) compareBy<QueueItem> { it.title.lowercase() } else compareBy { it.raw.optString("added") })
        .let { items -> if (ascending) items else items.reversed() }
    val historyItems = state.history
        .filter { item -> instanceFilter == null || item.instance.id == instanceFilter }
        .filter { item -> eventFilter == null || item.raw.optString("eventType").equals(eventFilter, ignoreCase = true) }
        .sortedByDescending { it.raw.optString("date") }

    Column(modifier.padding(horizontal = 16.dp)) {
        ActivityHero(state.queue.size, state.queue.count { it.isIssue }, showHistory)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                QueueHistoryButtonGroup(showHistory) { showHistory = it }
            }
            IconButton(viewModel::refreshDownloads) { Icon(Icons.Default.Refresh, "Refresh download clients") }
        }
        if (state.instances.size > 1) {
            FilterRow {
                FilterChip(instanceFilter == null, { instanceFilter = null }, label = { Text("All instances") })
                state.instances.forEach { instance ->
                    FilterChip(instanceFilter == instance.id, { instanceFilter = instance.id }, label = { Text(instance.displayName()) })
                }
            }
        }
        if (!showHistory) {
            FilterRow {
                FilterChip(protocolFilter == null, { protocolFilter = null }, label = { Text("All protocols") })
                listOf("Usenet", "Torrent").forEach { protocol ->
                    FilterChip(protocolFilter == protocol, { protocolFilter = protocol }, label = { Text(protocol) })
                }
                FilterChip(issuesOnly, { issuesOnly = !issuesOnly }, label = { Text("Issues") })
            }
            FilterRow {
                FilterChip(sortByTitle, { sortByTitle = true }, label = { Text("Title") })
                FilterChip(!sortByTitle, { sortByTitle = false }, label = { Text("Added") })
                FilterChip(ascending, { ascending = !ascending }, label = { Text(if (ascending) "Ascending" else "Newest first") })
            }
        } else {
            val events = state.history.map { it.raw.optString("eventType") }.filter { it.isNotBlank() }.distinct().sorted()
            if (events.isNotEmpty()) FilterRow {
                FilterChip(eventFilter == null, { eventFilter = null }, label = { Text("All events") })
                events.forEach { event -> FilterChip(eventFilter == event, { eventFilter = event }, label = { Text(event.replaceFirstChar { it.uppercase() }) }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (showHistory) HistoryList(Modifier.fillMaxSize(), historyItems) else QueueList(Modifier.fillMaxSize(), queueItems, viewModel, onSelect = { selectedQueueItem = it })
    }
    state.importFor?.let { item -> ManualImportSheet(item, state.importFiles, viewModel) }
    selectedQueueItem?.let { item -> QueueItemSheet(item, viewModel, onDismiss = { selectedQueueItem = null }) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueList(modifier: Modifier, items: List<QueueItem>, viewModel: RuddarrViewModel, onSelect: (QueueItem) -> Unit) {
    if (items.isEmpty()) EmptyState(modifier, "Queue is clear", "No active downloads were reported by Radarr or Sonarr.", "Refresh") { viewModel.refreshActivity() }
    else LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { "${it.instance.id}-${it.id}" }) { item ->
            Card(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text("${item.instance.displayName()} · ${item.status}", color = if (item.isIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.isIssue) Icon(Icons.Default.Close, "Queue issue", tint = MaterialTheme.colorScheme.error)
                    }
                    if (item.progress > 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearWavyProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text(listOfNotNull("${item.progress}%", item.timeLeft.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                    }
                    if (item.needsManualImport) {
                        Spacer(Modifier.height(8.dp))
                        AssistChip(onClick = { viewModel.loadManualImport(item) }, label = { Text("Manual import") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueItemSheet(item: QueueItem, viewModel: RuddarrViewModel, onDismiss: () -> Unit) {
    var showRemoval by remember(item.instance.id, item.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(item.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = if (item.isIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                Text(listOf(item.quality, item.size.takeIf { it > 0 }?.formatBytes(), item.protocol).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.size > 0) item {
                LinearWavyProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(listOf("${item.progress}%", item.sizeLeft.takeIf { it > 0 }?.formatBytes(), item.timeLeft.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" remaining · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.needsManualImport) item { Button(onClick = { viewModel.loadManualImport(item); onDismiss() }) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Manual import") } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val media = item.raw.optJSONObject(if (item.instance.service == ServiceType.RADARR) "movie" else "series")
                    media?.takeIf { it.optInt("id") > 0 }?.let { mediaRecord ->
                        OutlinedButton(onClick = { viewModel.open(MediaRecord(item.instance.service, mediaRecord, item.instance.id)); onDismiss() }) { Text("Open media") }
                    }
                    OutlinedButton(onClick = { showRemoval = true }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Remove") }
                }
            }
            item { HorizontalDivider() }
            item { Text("Information", style = MaterialTheme.typography.titleMedium) }
            item { QueueInformation("Instance", item.instance.displayName()) }
            item.takeIf { it.downloadClient.isNotBlank() }?.let { client -> item { QueueInformation("Client", client.downloadClient) } }
            item.takeIf { it.indexer.isNotBlank() }?.let { queue -> item { QueueInformation("Indexer", queue.indexer) } }
            item.takeIf { it.languages.isNotEmpty() }?.let { queue -> item { QueueInformation("Languages", queue.languages.joinToString(", ")) } }
            item.takeIf { it.customFormats.isNotEmpty() }?.let { queue -> item { QueueInformation("Custom formats", queue.customFormats.joinToString(", ")) } }
            item.customFormatScore?.let { score -> item { QueueInformation("Custom-format score", if (score >= 0) "+$score" else score.toString()) } }
            item.takeIf { it.added.isNotBlank() }?.let { queue -> item { QueueInformation("Added", queue.added) } }
            item.errorMessage.takeIf { it.isNotBlank() }?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (item.statusMessages.isNotEmpty()) {
                item { Text("Status messages", style = MaterialTheme.typography.titleMedium) }
                items(item.statusMessages, key = { "${it.title}-${it.messages.joinToString()}" }) { message ->
                    Column {
                        Text(message.title.takeIf { it.isNotBlank() } ?: "Status", style = MaterialTheme.typography.titleSmall)
                        message.messages.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    if (showRemoval) QueueRemovalDialog(item, viewModel, onDismiss = { showRemoval = false; onDismiss() })
}

@Composable
private fun QueueInformation(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.padding(start = 16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QueueRemovalDialog(item: QueueItem, viewModel: RuddarrViewModel, onDismiss: () -> Unit) {
    var removeFromClient by remember(item.instance.id, item.id) { mutableStateOf(false) }
    var blocklist by remember(item.instance.id, item.id) { mutableStateOf(false) }
    var searchReplacement by remember(item.instance.id, item.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove queue task?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title)
                FilterChip(removeFromClient, { removeFromClient = !removeFromClient }, label = { Text(if (removeFromClient) "Remove from client" else "Keep in client") })
                FilterChip(blocklist, { blocklist = !blocklist }, label = { Text(if (blocklist) "Blocklist release" else "Do not blocklist") })
                if (blocklist) FilterChip(searchReplacement, { searchReplacement = !searchReplacement }, label = { Text(if (searchReplacement) "Search replacement" else "Do not search") })
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.removeQueue(item, removeFromClient, blocklist, searchReplacement); onDismiss() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualImportSheet(item: QueueItem, files: List<org.json.JSONObject>, viewModel: RuddarrViewModel) {
    var selected by remember(item.id, files.size) {
        mutableStateOf(files.filterNot { it.rejections().any { reason -> reason.equals("sample", true) } }.map { it.optString("path") }.toSet())
    }
    ModalBottomSheet(onDismissRequest = viewModel::closeManualImport) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Manual import", style = MaterialTheme.typography.headlineSmall)
                Text(item.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (files.isEmpty()) item { Text("No importable files found.") }
            items(files, key = { it.optString("path", it.optString("name")) }) { file ->
                val path = file.optString("path", file.optString("relativePath", file.optString("name", "Unknown file")))
                val reasons = file.rejections()
                FilterChip(
                    selected = selected.contains(file.optString("path")),
                    onClick = {
                        val key = file.optString("path")
                        selected = if (selected.contains(key)) selected - key else selected + key
                    },
                    label = {
                        Column {
                            Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(file.manualImportSummary(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            reasons.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        }
                    },
                )
            }
            item {
                Button(
                    onClick = { viewModel.importFiles(item, files.filter { selected.contains(it.optString("path")) }) },
                    enabled = selected.isNotEmpty(),
                ) { Text("Import selected (${selected.size})") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun HistoryList(modifier: Modifier, items: List<HistoryItem>) {
    if (items.isEmpty()) EmptyState(modifier, "No history", "Events will appear after downloads and imports.", null) {}
    else LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { items(items, key = { "${it.instance.id}-${it.title}-${it.date}" }) { item -> Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.title); Text("${item.eventType} · ${item.date}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
}

@Composable
private fun SettingsScreen(modifier: Modifier, state: AppState, viewModel: RuddarrViewModel) {
    var editorFor by remember { mutableStateOf<InstanceConfig?>(null) }
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Instances", style = MaterialTheme.typography.headlineSmall); Text("Credentials are encrypted with Android Keystore.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { editorFor = InstanceConfig(service = ServiceType.RADARR, label = "", primaryUrl = "", apiKey = "") }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add") }
            }
        }
        items(state.instances, key = { it.id }) { instance -> InstanceSettingsCard(instance, state.metadata[instance.id], viewModel, onEdit = { editorFor = instance }) }
        item { HorizontalDivider() }
        item { Text("System", style = MaterialTheme.typography.titleLarge) }
        item { Text("Android keeps local settings on this device. iCloud synchronization, StoreKit subscriptions, and APNs registration depend on Apple-only services and are not included in this Android client.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (BuildConfig.DEBUG) item {
            OutlinedButton(onClick = viewModel::loadDebugDemo) { Text("Load local demo data") }
            Text("Creates local, non-network sample data for layout checks and screenshots.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = viewModel::seedDebugInstances) { Text("Load debug instance seeds") }
            Text("Reads app/src/debug/assets/seed-instances.json from this debug build only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    editorFor?.let { target -> InstanceEditor(target, onDismiss = { editorFor = null }, onSave = { viewModel.saveInstance(it); editorFor = null }) }
    state.notificationFor?.let { instance -> InstanceNotificationsSheet(instance, state.notifications, viewModel) }
}

@Composable
private fun InstanceSettingsCard(instance: InstanceConfig, metadata: ServiceMetadata?, viewModel: RuddarrViewModel, onEdit: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(instance.displayName(), style = MaterialTheme.typography.titleLarge); Text(instance.service.apiLabel, color = MaterialTheme.colorScheme.primary) }; IconButton(onEdit) { Icon(Icons.Default.Edit, "Edit instance") }; IconButton({ viewModel.deleteInstance(instance) }) { Icon(Icons.Default.Delete, "Delete instance") } }
            Text(instance.primaryUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            metadata?.let {
                Text(listOf(it.name, it.version).filter { value -> value.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                it.diskSpace.forEach { disk -> Text("${disk.label}: ${disk.freeSpace.formatBytes()} free of ${disk.totalSpace.formatBytes()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ viewModel.refreshMetadata(instance) }) { Text("Test & refresh") }
                OutlinedButton({ viewModel.sendCommand(instance, "RescanFolders") }) { Text("Rescan") }
                OutlinedButton({ viewModel.loadNotifications(instance) }) {
                    Text("Webhooks", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstanceNotificationsSheet(instance: InstanceConfig, notifications: List<org.json.JSONObject>, viewModel: RuddarrViewModel) {
    ModalBottomSheet(onDismissRequest = viewModel::closeNotifications) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Webhooks", style = MaterialTheme.typography.headlineSmall)
                Text("${instance.displayName()} notifications", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (notifications.isEmpty()) item { Text("No notification connections are configured on this instance.") }
            items(notifications, key = { it.optInt("id") }) { notification ->
                NotificationEditorCard(instance, notification, viewModel)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun NotificationEditorCard(instance: InstanceConfig, notification: org.json.JSONObject, viewModel: RuddarrViewModel) {
    var onGrab by remember(notification.optInt("id")) { mutableStateOf(notification.optBoolean("onGrab")) }
    var onDownload by remember(notification.optInt("id")) { mutableStateOf(notification.optBoolean("onDownload")) }
    var onUpgrade by remember(notification.optInt("id")) { mutableStateOf(notification.optBoolean("onUpgrade")) }
    var onAdded by remember(notification.optInt("id")) {
        mutableStateOf(notification.optBoolean(if (instance.service == ServiceType.RADARR) "onMovieAdded" else "onSeriesAdd"))
    }
    var onImport by remember(notification.optInt("id")) { mutableStateOf(notification.optBoolean("onImportComplete")) }
    var onHealth by remember(notification.optInt("id")) { mutableStateOf(notification.optBoolean("onHealthIssue")) }
    var deleteConfirmation by remember(notification.optInt("id")) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(notification.optString("name", "Webhook"), style = MaterialTheme.typography.titleMedium)
                    Text(notification.optString("implementation", "Notification"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ deleteConfirmation = true }) { Icon(Icons.Default.Delete, "Delete webhook") }
            }
            FilterChip(onGrab, { onGrab = !onGrab }, label = { Text("Grab") })
            FilterChip(onDownload, { onDownload = !onDownload }, label = { Text("Download") })
            FilterChip(onUpgrade, { onUpgrade = !onUpgrade }, label = { Text("Upgrade") })
            FilterChip(onAdded, { onAdded = !onAdded }, label = { Text(if (instance.service == ServiceType.RADARR) "Movie added" else "Series added") })
            if (instance.service == ServiceType.SONARR) FilterChip(onImport, { onImport = !onImport }, label = { Text("Import complete") })
            FilterChip(onHealth, { onHealth = !onHealth }, label = { Text("Health issue") })
            Button(onClick = {
                viewModel.saveNotification(instance, org.json.JSONObject(notification.toString()).apply {
                    put("onGrab", onGrab)
                    put("onDownload", onDownload)
                    put("onUpgrade", onUpgrade)
                    put(if (instance.service == ServiceType.RADARR) "onMovieAdded" else "onSeriesAdd", onAdded)
                    if (instance.service == ServiceType.SONARR) put("onImportComplete", onImport)
                    put("onHealthIssue", onHealth)
                })
            }) { Text("Save events") }
        }
    }
    if (deleteConfirmation) AlertDialog(
        onDismissRequest = { deleteConfirmation = false },
        title = { Text("Delete ${notification.optString("name", "webhook")}?" ) },
        text = { Text("This removes the notification connection from ${instance.service.apiLabel}.") },
        confirmButton = { TextButton(onClick = { deleteConfirmation = false; viewModel.deleteNotification(instance, notification) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("Cancel") } },
    )
}

@Composable
private fun InstanceEditor(instance: InstanceConfig, onDismiss: () -> Unit, onSave: (InstanceConfig) -> Unit) {
    var service by remember(instance.id) { mutableStateOf(instance.service) }
    var label by remember(instance.id) { mutableStateOf(instance.label) }
    var url by remember(instance.id) { mutableStateOf(instance.primaryUrl) }
    var alternate by remember(instance.id) { mutableStateOf(instance.alternateUrl) }
    var apiKey by remember(instance.id) { mutableStateOf(instance.apiKey) }
    var headers by remember(instance.id) { mutableStateOf(instance.headers.joinToString("\n") { "${it.name}: ${it.value}" }) }
    var mode by remember(instance.id) { mutableStateOf(instance.mode) }
    var apiKeyVisible by remember(instance.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (instance.primaryUrl.isBlank()) "Add instance" else "Edit instance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(service == ServiceType.RADARR, { service = ServiceType.RADARR }, label = { Text("Radarr") }); FilterChip(service == ServiceType.SONARR, { service = ServiceType.SONARR }, label = { Text("Sonarr") }) }
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Label") }, singleLine = true)
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL") }, singleLine = true, supportingText = { Text("https://radarr.example.com") })
                OutlinedTextField(alternate, { alternate = it }, Modifier.fillMaxWidth(), label = { Text("Alternate URL (optional)") }, singleLine = true)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (apiKeyVisible) "Hide API key" else "Show API key")
                        }
                    },
                )
                OutlinedTextField(headers, { headers = it }, Modifier.fillMaxWidth(), label = { Text("Custom headers") }, supportingText = { Text("One `Name: value` header per line") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == InstanceMode.NORMAL, { mode = InstanceMode.NORMAL }, label = { Text("Normal connection") })
                    FilterChip(mode == InstanceMode.SLOW, { mode = InstanceMode.SLOW }, label = { Text("Slow connection") })
                }
            }
        },
        confirmButton = { Button(onClick = {
            val parsedHeaders = headers.lineSequence().mapNotNull { line ->
                line.substringBefore(':').trim().takeIf { it.isNotBlank() }?.let { name -> InstanceHeader(name, line.substringAfter(':', "").trim()) }
            }.toList()
            onSave(instance.copy(service = service, label = label, primaryUrl = url.trim(), alternateUrl = alternate.trim(), apiKey = apiKey.trim(), headers = parsedHeaders, mode = mode))
        }, enabled = instance.copy(service = service, primaryUrl = url.trim(), alternateUrl = alternate.trim(), apiKey = apiKey.trim()).validationError() == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyState(modifier: Modifier, title: String, description: String, action: String?, onAction: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(32.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall); Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); action?.let { Button(onClick = onAction) { Text(it) } } } }
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun org.json.JSONArray?.toIntSet(): Set<Int> = this?.let { array ->
    buildSet { repeat(array.length()) { add(array.optInt(it)) } }.filter { it > 0 }.toSet()
}.orEmpty()

private fun org.json.JSONObject.summary(): String = buildList {
    releaseQuality().takeIf { it.isNotBlank() }?.let(::add)
    optString("indexer").takeIf { it.isNotBlank() }?.let(::add)
    optLong("size").takeIf { it > 0 }?.formatBytes()?.let(::add)
    optString("protocol").takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" · ")

private fun org.json.JSONObject.releaseQuality(): String = optJSONObject("quality")?.optJSONObject("quality")?.optString("name").orEmpty()

private fun org.json.JSONObject.releaseLanguages(): List<String> = optJSONArray("languages")?.let { languages ->
    List(languages.length()) { index -> languages.optJSONObject(index)?.optString("name").orEmpty() }.filter { it.isNotBlank() }
}.orEmpty()

private fun org.json.JSONObject.releaseCustomFormats(): List<String> = optJSONArray("customFormats")?.let { formats ->
    List(formats.length()) { index -> formats.optJSONObject(index)?.optString("name").orEmpty() }.filter { it.isNotBlank() }
}.orEmpty()

private fun org.json.JSONObject.rejections(): List<String> = optJSONArray("rejections")?.let { rejections ->
    List(rejections.length()) { index -> rejections.optJSONObject(index)?.optString("reason").orEmpty() }.filter { it.isNotBlank() }
}.orEmpty()

private fun org.json.JSONObject.manualImportSummary(): String = buildList {
    optJSONObject("quality")?.optJSONObject("quality")?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
    optLong("size").takeIf { it > 0 }?.formatBytes()?.let(::add)
    optJSONArray("languages")?.let { languages ->
        List(languages.length()) { index -> languages.optJSONObject(index)?.optString("name").orEmpty() }.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let(::add)
    }
}.joinToString(" · ")
