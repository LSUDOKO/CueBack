package com.cueback.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.BuildConfig
import com.cueback.app.billing.EntitlementState
import com.cueback.app.billing.FeatureGate
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.MatchBand
import com.cueback.app.data.repo.ReentryRecord
import com.cueback.app.detect.DetectionHealth
import com.cueback.app.detect.Suggestion
import com.cueback.app.ui.components.AppIcon
import com.cueback.app.ui.components.ConfidenceText
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.RibbonNext
import com.cueback.app.ui.components.SectionTitle
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.components.relativeTime
import com.cueback.app.ui.theme.NoteSerif
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class HomeUi(
    val open: List<ContextCapsule> = emptyList(),
    val suggestion: Suggestion? = null,
    val health: DetectionHealth? = null,
    val trackedLabels: List<String> = emptyList(),
    val entitlement: EntitlementState = EntitlementState.Loading,
    val recent: List<ReentryRecord> = emptyList(),
    val limitNotice: Boolean = false,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val health = MutableStateFlow<DetectionHealth?>(null)

    val ui: StateFlow<HomeUi> = combine(
        c.contexts.observeOpen(),
        c.detection.suggestion,
        combine(health, c.settings.settings) { h, s -> h to s.trackedApps.map(c.catalog::labelOf).sorted() },
        combine(c.billing.state, c.reentry.observeRecentCompleted(5)) { e, r -> e to r },
        c.detection.archivedForLimit,
    ) { open, sug, (h, labels), (ent, recent), limit ->
        HomeUi(open, sug?.takeIf { s -> open.any { it.id == s.contextId } }, h, labels, ent, recent, limit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun refresh() {
        viewModelScope.launch {
            runCatching { c.detection.runPass() }
            health.value = c.detection.health()
        }
    }

    fun dismissLimit() = c.detection.dismissLimitNotice()

    fun runDemo(onReady: (String) -> Unit) {
        viewModelScope.launch { c.detection.replayDemo()?.let(onReady) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onWarmStart: (String) -> Unit,
    onContext: (String) -> Unit,
    onCapture: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onFixDetection: () -> Unit,
    onPaywall: () -> Unit,
) {
    val vm = containerViewModel { HomeViewModel(it) }
    val ui by vm.ui.collectAsState()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("CueBack", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onLibrary) { Icon(Icons.Default.Search, contentDescription = "Search contexts") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCapture,
                modifier = Modifier.semantics { contentDescription = "Save my place" },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Save my place") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = pad.calculateTopPadding(), bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                    Text("Pick up where you left off.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { HealthLine(ui, onFixDetection) }
            if (ui.limitNotice) item {
                Notice(
                    "Free keeps 3 open contexts, so the oldest one moved to your library archive.",
                    action = "See Pro" to onPaywall,
                    dismiss = vm::dismissLimit,
                )
            }
            val hero = ui.suggestion
            if (hero != null) {
                val ctx = ui.open.first { it.id == hero.contextId }
                item { SuggestionCard(ctx, hero) { onWarmStart(ctx.id) } }
            }
            val rest = ui.open.filter { it.id != hero?.contextId }
            if (ui.open.isEmpty()) item { EmptyState(watching = ui.trackedLabels.isNotEmpty(), onCapture) { vm.runDemo(onWarmStart) } }
            else if (rest.isNotEmpty()) item { SectionTitle(if (hero != null) "Also paused" else "Open contexts") }
            items(rest, key = { it.id }) { ctx -> ContextCard(ctx, onResume = { onWarmStart(ctx.id) }, onOpen = { onContext(ctx.id) }) }
            if (ui.recent.isNotEmpty()) item { ReentryStrip(ui.recent) }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Working late"
}

@Composable
private fun HealthLine(ui: HomeUi, onFix: () -> Unit) {
    val (text, action) = when (ui.health) {
        DetectionHealth.OFF_NO_APPS -> "Automatic detection is off until you choose which apps to watch." to "Choose apps"
        DetectionHealth.OFF_NO_PERMISSION -> "Automatic detection needs usage access." to "Allow access"
        DetectionHealth.PAUSED -> "Detection is paused. Manual saves still work." to "Resume"
        null -> return
        DetectionHealth.ACTIVE -> {
            if (ui.trackedLabels.isEmpty()) return
            val gate = FeatureGate(ui.entitlement)
            val watched = ui.trackedLabels.take(gate.maxTrackedApps.coerceAtMost(ui.trackedLabels.size))
            "Watching ${watched.joinToString(", ")} for pauses and returns." to null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hint(text, Modifier.weight(1f))
        action?.let { TextButton(onClick = onFix) { Text(it) } }
    }
}

@Composable
private fun SuggestionCard(ctx: ContextCapsule, s: Suggestion, onResume: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(ctx.primaryApp, size = 22)
                Text(
                    if (s.match.band == MatchBand.AUTO) "Looks like you're back" else "Were you returning to this?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(ctx.title.text, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = NoteSerif))
            Hint("Paused ${relativeTime(ctx.pausedAt)}")
            RibbonNext(ctx.nextAction, large = false)
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume") }
        }
    }
}

@Composable
private fun ContextCard(ctx: ContextCapsule, onResume: () -> Unit, onOpen: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open context", onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(ctx.primaryApp, size = 20)
                Text(ctx.title.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (ctx.isDemo) Text("Demo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                ctx.nextAction?.let { "Next: ${it.text}" } ?: "Next step not recorded yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ctx.status.name == "ACTIVE") "In progress" else "Paused ${relativeTime(ctx.pausedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("  ", style = MaterialTheme.typography.labelSmall)
                ConfidenceText(ctx.confidence, Modifier.weight(1f))
                OutlinedButton(onClick = onResume, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("Resume") }
            }
        }
    }
}

@Composable
private fun ReentryStrip(recent: List<ReentryRecord>) {
    val secs = recent.mapNotNull { it.session.reentryMs?.div(1000) }
    if (secs.isEmpty()) return
    val median = secs.sorted()[secs.size / 2]
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Your recent re-entries")
        Text("Typically back in ${com.cueback.app.ui.warmstart.formatSecondsShort(median.toLong())}", style = MaterialTheme.typography.titleLarge.copy(fontFamily = NoteSerif))
        Hint("Last ${secs.size}: " + secs.joinToString(", ") { com.cueback.app.ui.warmstart.formatSecondsShort(it.toLong()) })
    }
}

@Composable
private fun EmptyState(watching: Boolean, onCapture: () -> Unit, onDemo: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (watching) {
            Text("You're all set.", style = MaterialTheme.typography.headlineMedium)
            Hint("CueBack will quietly remember where you stop in the apps you chose. You can also save your place yourself at any time.")
        } else {
            Text("Nothing saved yet.", style = MaterialTheme.typography.headlineMedium)
            Hint("Save your place by hand whenever you step away, or choose apps to watch in Settings so CueBack does it for you.")
        }
        OutlinedButton(onClick = onCapture) { Text("Save my place now") }
        if (BuildConfig.DEMO_FIXTURES) {
            TextButton(onClick = onDemo) { Text("Replay the demo story (debug build)") }
        }
    }
}

@Composable
private fun Notice(text: String, action: Pair<String, () -> Unit>?, dismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row {
                action?.let { (label, f) -> TextButton(onClick = f) { Text(label) } }
                TextButton(onClick = dismiss) { Text("Dismiss") }
            }
        }
    }
}
