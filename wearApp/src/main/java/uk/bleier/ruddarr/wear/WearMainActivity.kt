package uk.bleier.ruddarr.wear

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.launch

class WearMainActivity : ComponentActivity() {
    private val viewModel by viewModels<WearViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RuddarrWearApp(viewModel) }
    }
}

data class WearUiState(
    val instances: List<WatchInstance> = emptyList(),
    val snapshots: Map<WatchService, WatchSnapshot> = emptyMap(),
    val loading: Boolean = false,
    val message: String? = null,
)

class WearViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WearInstanceStore(application)
    private val api = WearArrApi()

    var state by mutableStateOf(WearUiState())
        private set

    init {
        reload()
    }

    fun instance(service: WatchService): WatchInstance? = state.instances.firstOrNull { it.service == service }

    fun reload() {
        state = state.copy(instances = store.load())
        refresh()
    }

    fun save(instance: WatchInstance) {
        val updated = state.instances.filterNot { it.service == instance.service } + instance
        store.save(updated)
        state = state.copy(instances = updated, message = "${instance.service.apiLabel} saved")
        refresh()
    }

    fun remove(service: WatchService) {
        val updated = state.instances.filterNot { it.service == service }
        store.save(updated)
        state = state.copy(instances = updated, snapshots = state.snapshots - service, message = "${service.apiLabel} removed")
    }

    fun refresh() {
        val configured = state.instances.filter(WatchInstance::isConfigured)
        if (configured.isEmpty()) return
        viewModelScope.launch {
            state = state.copy(loading = true, message = null)
            val snapshots = state.snapshots.toMutableMap()
            var failure: String? = null
            configured.forEach { instance ->
                runCatching { api.snapshot(instance) }
                    .onSuccess { snapshots[instance.service] = it }
                    .onFailure { failure = it.message ?: "Could not reach ${instance.service.apiLabel}." }
            }
            state = state.copy(snapshots = snapshots, loading = false, message = failure)
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }
}

private enum class WearDestination {
    HOME,
    MOVIES,
    SERIES,
    SETTINGS;

    val route: String get() = name.lowercase()
}

@Composable
private fun RuddarrWearApp(viewModel: WearViewModel) {
    val context = LocalContext.current
    val state = viewModel.state
    val colorScheme = dynamicColorScheme(context) ?: ColorScheme()

    MaterialTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive()) {
        AppScaffold {
            val navigation = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(
                navController = navigation,
                startDestination = WearDestination.HOME.route,
            ) {
                composable(WearDestination.HOME.route) {
                    WearHome(
                        state = state,
                        onMovies = {
                            navigation.navigate(
                                if (viewModel.instance(WatchService.RADARR)?.isConfigured == true) WearDestination.MOVIES.route else WearDestination.SETTINGS.route,
                            )
                        },
                        onSeries = {
                            navigation.navigate(
                                if (viewModel.instance(WatchService.SONARR)?.isConfigured == true) WearDestination.SERIES.route else WearDestination.SETTINGS.route,
                            )
                        },
                        onSettings = { navigation.navigate(WearDestination.SETTINGS.route) },
                        onRefresh = viewModel::refresh,
                    )
                }
                composable(WearDestination.MOVIES.route) {
                    LibraryScreen(
                        snapshot = state.snapshots[WatchService.RADARR],
                        service = WatchService.RADARR,
                        loading = state.loading,
                        message = state.message,
                        onRefresh = viewModel::refresh,
                    )
                }
                composable(WearDestination.SERIES.route) {
                    LibraryScreen(
                        snapshot = state.snapshots[WatchService.SONARR],
                        service = WatchService.SONARR,
                        loading = state.loading,
                        message = state.message,
                        onRefresh = viewModel::refresh,
                    )
                }
                composable(WearDestination.SETTINGS.route) {
                    SettingsScreen(
                        state = state,
                        onEdit = { service -> navigation.navigate("setup/${service.name}") },
                    )
                }
                composable("setup/{service}") { entry ->
                    val service = entry.arguments?.getString("service")
                        ?.let { runCatching { WatchService.valueOf(it) }.getOrNull() }
                        ?: WatchService.RADARR
                    InstanceEditor(
                        service = service,
                        instance = viewModel.instance(service),
                        onSave = {
                            viewModel.save(it)
                            navigation.popBackStack()
                        },
                        onDelete = {
                            viewModel.remove(service)
                            navigation.popBackStack()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WearHome(
    state: WearUiState,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader { Text("Ruddarr", style = MaterialTheme.typography.titleLarge) }
            }
            item {
                Text(
                    text = state.message ?: "Your local library, on your wrist.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item {
                ServiceButton(
                    service = WatchService.RADARR,
                    snapshot = state.snapshots[WatchService.RADARR],
                    configured = state.instances.any { it.service == WatchService.RADARR && it.isConfigured },
                    onClick = onMovies,
                )
            }
            item {
                ServiceButton(
                    service = WatchService.SONARR,
                    snapshot = state.snapshots[WatchService.SONARR],
                    configured = state.instances.any { it.service == WatchService.SONARR && it.isConfigured },
                    onClick = onSeries,
                )
            }
            item {
                FilledTonalButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !state.loading && state.instances.any(WatchInstance::isConfigured),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.loading) "Refreshing" else "Refresh local status")
                }
            }
            item {
                Button(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("Connections") }
            }
        }
    }
}

@Composable
private fun ServiceButton(
    service: WatchService,
    snapshot: WatchSnapshot?,
    configured: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 76.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (service == WatchService.RADARR) "Movies" else "Series", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    snapshot != null -> "${snapshot.total} local · ${snapshot.queueCount} queued"
                    configured -> "Tap to load ${service.apiLabel}"
                    else -> "Configure ${service.apiLabel}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    snapshot: WatchSnapshot?,
    service: WatchService,
    loading: Boolean,
    message: String?,
    onRefresh: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader { Text(if (service == WatchService.RADARR) "Movies" else "Series") }
            }
            item {
                if (snapshot == null) {
                    Text(
                        text = message ?: if (loading) "Contacting ${service.apiLabel}…" else "No local data yet.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LibrarySummary(snapshot)
                }
            }
            snapshot?.items?.forEach { item ->
                item {
                    LibraryRow(item)
                }
            }
            item {
                FilledTonalButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !loading,
                ) { Text(if (loading) "Refreshing" else "Refresh") }
            }
        }
    }
}

@Composable
private fun LibrarySummary(snapshot: WatchSnapshot) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Metric("Library", snapshot.total.toString(), Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Metric("Wanted", snapshot.wanted.toString(), Modifier.weight(1f))
            Metric("Queue", snapshot.queueCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryRow(item: WatchLibraryItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(if (item.available) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsScreen(state: WearUiState, onEdit: (WatchService) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ListHeader { Text("Connections") } }
            item {
                Text(
                    "Credentials stay encrypted on this watch. Use the same local URLs as on your phone.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WatchService.entries.forEach { service ->
                item {
                    val configured = state.instances.firstOrNull { it.service == service }?.isConfigured == true
                    Button(
                        onClick = { onEdit(service) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(service.apiLabel, style = MaterialTheme.typography.titleMedium)
                            Text(if (configured) "Configured" else "Not configured", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceEditor(
    service: WatchService,
    instance: WatchInstance?,
    onSave: (WatchInstance) -> Unit,
    onDelete: () -> Unit,
) {
    val url = rememberTextFieldState(initialText = instance?.baseUrl.orEmpty())
    val apiKey = rememberTextFieldState(initialText = instance?.apiKey.orEmpty())
    var error by rememberSaveable(service) { mutableStateOf<String?>(null) }
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ListHeader { Text("${service.apiLabel} setup") } }
            item {
                Text(
                    "The watch connects directly to your local ${service.apiLabel} server.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                WatchTextField(
                    label = "Server URL",
                    state = url,
                    placeholder = "http://server:port",
                    keyboardType = KeyboardType.Uri,
                )
            }
            item {
                WatchTextField(
                    label = "API key",
                    state = apiKey,
                    placeholder = "Local API key",
                    keyboardType = KeyboardType.Password,
                    secret = true,
                )
            }
            error?.let { message ->
                item {
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val candidate = WatchInstance(service, url.text.toString().trim(), apiKey.text.toString().trim())
                        error = candidate.validationError()
                        if (error == null) onSave(candidate)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("Save connection") }
            }
            if (instance != null) {
                item {
                    FilledTonalButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("Remove ${service.apiLabel}") }
                }
            }
        }
    }
}

@Composable
private fun WatchTextField(
    label: String,
    state: TextFieldState,
    placeholder: String,
    keyboardType: KeyboardType,
    secret: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (state.text.isBlank()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                outputTransformation = if (secret) {
                    { replace(0, length, "•".repeat(length)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        }
    }
}
