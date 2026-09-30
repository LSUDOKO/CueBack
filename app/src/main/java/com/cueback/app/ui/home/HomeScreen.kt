package com.cueback.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
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
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.MatchBand
import com.cueback.app.core.model.NoteKind
import com.cueback.app.core.model.UseCase
import com.cueback.app.core.model.WorkEvent
import com.cueback.app.data.repo.ReentryRecord
import com.cueback.app.detect.DetectionHealth
import com.cueback.app.detect.Suggestion
import com.cueback.app.ui.components.AppIcon
import com.cueback.app.ui.components.ConfidenceText
import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.GlassButton
import com.cueback.app.ui.components.GlassCard
import com.cueback.app.ui.components.GlassIconButton
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.Mascot
import com.cueback.app.ui.components.MascotAvatar
import com.cueback.app.ui.components.MascotPose
import com.cueback.app.ui.components.MascotSays
import com.cueback.app.ui.components.OrbButton
import com.cueback.app.ui.components.RibbonNext
import com.cueback.app.ui.components.VSpace
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.components.glass
import com.cueback.app.ui.components.relativeTime
import com.cueback.app.ui.theme.Backdrop
import com.cueback.app.ui.theme.Ember
import com.cueback.app.ui.theme.EmberBackdrop
import com.cueback.app.ui.warmstart.formatSecondsShort
import kotlinx.coroutines.delay
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
    val useCase: UseCase = UseCase.GENERAL,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val health = MutableStateFlow<DetectionHealth?>(null)

    val ui: StateFlow<HomeUi> = combine(
        c.contexts.observeOpen(),
        c.detection.suggestion,
        combine(health, c.settings.settings) { h, s -> Triple(h, s.trackedApps.map(c.catalog::labelOf).sorted(), s.useCase) },
        combine(c.billing.state, c.reentry.observeRecentCompleted(5)) { e, r -> e to r },
        c.detection.archivedForLimit,
    ) { open, sug, (h, labels, useCase), (ent, recent), limit ->
        HomeUi(open, sug?.takeIf { s -> open.any { it.id == s.contextId } }, h, labels, ent, recent, limit, useCase)
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

    /**
     * Saves a place from the one line typed on home. Same rule as the full capture screen: with a live
     * session the note joins what was observed and the session pauses; otherwise a manual context is made.
     */
    fun quickSave(next: String, useCase: UseCase, onSaved: () -> Unit) {
        val text = next.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val now = c.clock()
            if (c.detection.openSessionActive()) {
                c.detection.runPass(
                    listOf(
                        WorkEvent(now - 1, EventType.MANUAL_NOTE, noteKind = NoteKind.NEXT, note = text),
                        WorkEvent(now, EventType.EXPLICIT_PAUSE),
                    ),
                )
            } else {
                c.detection.saveManual(c.builder.manual(c.newId(), now, "", null, null, null, text, null, useCase))
            }
            onSaved()
        }
    }
}

private val SoftGlass = Color(0x24FFF3E8)

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
    var draft by rememberSaveable { mutableStateOf("") }
    var useCase by remember(ui.useCase) { mutableStateOf(ui.useCase) }
    var justSaved by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(3200)
            justSaved = false
        }
    }

    EmberBackdrop(Backdrop.Dawn) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Header(onLibrary, onSettings)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                VSpace(30)
                Text(
                    "Pick up where you left off.",
                    style = MaterialTheme.typography.displaySmall,
                    color = Ember.Cream,
                    // Narrow measure, so it breaks as "Pick up where / you left off." instead of stranding one word.
                    modifier = Modifier.padding(horizontal = 22.dp).widthIn(max = 270.dp).semantics { heading() },
                )
                VSpace(14)
                HealthLine(ui, onFixDetection, Modifier.padding(horizontal = 20.dp))
                if (ui.limitNotice) {
                    VSpace(12)
                    Notice(
                        "Free keeps 3 open contexts, so the oldest one moved to your library archive.",
                        action = "See Pro" to onPaywall,
                        dismiss = vm::dismissLimit,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                val hero = ui.suggestion
                if (hero != null) {
                    val ctx = ui.open.first { it.id == hero.contextId }
                    VSpace(16)
                    SuggestionCard(ctx, hero, Modifier.padding(horizontal = 20.dp)) { onWarmStart(ctx.id) }
                }
                val rest = ui.open.filter { it.id != hero?.contextId }
                VSpace(18)
                if (ui.open.isEmpty()) {
                    EmptyState(
                        watching = ui.trackedLabels.isNotEmpty(),
                        onCapture = onCapture,
                        onChooseApps = onFixDetection,
                        onDemo = { vm.runDemo(onWarmStart) },
                    )
                } else if (rest.isNotEmpty()) {
                    Text(
                        if (hero != null) "Also paused" else "Open contexts",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ember.Ash,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    VSpace(10)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rest.forEach { ctx -> ContextCard(ctx, onResume = { onWarmStart(ctx.id) }, onOpen = { onContext(ctx.id) }) }
                    }
                }
                if (ui.recent.isNotEmpty()) ReentryStrip(ui.recent, Modifier.padding(horizontal = 22.dp))
                VSpace(16)
            }
            CapturePanel(
                draft = draft,
                onDraft = { if (it.length <= 300) draft = it },
                useCase = useCase,
                onUseCase = { useCase = it },
                justSaved = justSaved,
                onDetails = onCapture,
                onSave = {
                    if (draft.isBlank()) onCapture()
                    else vm.quickSave(draft, useCase) {
                        draft = ""
                        justSaved = true
                        keyboard?.hide()
                    }
                },
            )
        }
    }
}

@Composable
private fun Header(onLibrary: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).glass(CircleShape, SoftGlass), contentAlignment = Alignment.Center) {
            MascotAvatar(size = 34.dp)
        }
        Column(Modifier.weight(1f)) {
            Text("${greeting()},", style = MaterialTheme.typography.labelMedium, color = Ember.Cream.copy(alpha = 0.9f))
            Text("Welcome back", style = MaterialTheme.typography.titleSmall, color = Ember.Cream)
        }
        GlassIconButton(Icons.Default.Search, "Search contexts", onLibrary, fill = SoftGlass)
        GlassIconButton(Icons.Default.Settings, "Settings", onSettings, fill = SoftGlass)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Working late"
}

@Composable
private fun HealthLine(ui: HomeUi, onFix: () -> Unit, modifier: Modifier = Modifier) {
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
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .then(if (action != null) Modifier.clickable(onClickLabel = action, onClick = onFix) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ember.Cream.copy(alpha = 0.86f), modifier = Modifier.weight(1f))
        action?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Ember.Glow) }
    }
}

@Composable
private fun SuggestionCard(ctx: ContextCapsule, s: Suggestion, modifier: Modifier = Modifier, onResume: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), fill = Ember.GlassStrong, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(ctx.primaryApp, size = 20)
                    Text(
                        if (s.match.band == MatchBand.AUTO) "Looks like you're back" else "Were you returning to this?",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ember.Glow,
                    )
                }
                Text(ctx.title.text, style = MaterialTheme.typography.headlineSmall, color = Ember.Cream)
                Hint("Paused ${relativeTime(ctx.pausedAt)}")
            }
            Mascot(MascotPose.Wave, size = 78.dp)
        }
        RibbonNext(ctx.nextAction, large = false)
        EmberButton("Resume", onResume, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ContextCard(ctx: ContextCapsule, onResume: () -> Unit, onOpen: () -> Unit) {
    GlassCard(
        Modifier.width(222.dp).height(172.dp),
        onClick = onOpen,
        onClickLabel = "Open context",
        padding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(ctx.primaryApp, size = 22)
            Spacer(Modifier.weight(1f))
            if (ctx.isDemo) Text("Demo", style = MaterialTheme.typography.labelSmall, color = Ember.Glow)
        }
        VSpace(10)
        Text(ctx.title.text, style = MaterialTheme.typography.titleMedium, color = Ember.Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
        VSpace(2)
        Text(
            ctx.nextAction?.let { "Next: ${it.text}" } ?: "Next step not recorded yet",
            style = MaterialTheme.typography.bodySmall,
            color = Ember.Ash,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (ctx.status.name == "ACTIVE") "In progress" else "Paused ${relativeTime(ctx.pausedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ember.Ash,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ConfidenceText(ctx.confidence)
            }
            EmberButton("Resume", onResume, compact = true)
        }
    }
}

@Composable
private fun ReentryStrip(recent: List<ReentryRecord>, modifier: Modifier = Modifier) {
    val secs = recent.mapNotNull { it.session.reentryMs?.div(1000) }
    if (secs.isEmpty()) return
    val median = secs.sorted()[secs.size / 2]
    Column(modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Your recent re-entries", style = MaterialTheme.typography.titleSmall, color = Ember.Ash)
        Text("Typically back in ${formatSecondsShort(median.toLong())}", style = MaterialTheme.typography.headlineSmall, color = Ember.Cream)
        Hint("Last ${secs.size}: " + secs.joinToString(", ") { formatSecondsShort(it.toLong()) })
    }
}

@Composable
private fun EmptyState(watching: Boolean, onCapture: () -> Unit, onChooseApps: () -> Unit, onDemo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MascotSays(
            if (watching) "You're all set. I'll quietly remember where you stop in the apps you chose."
            else "Nothing saved yet. Save your place by hand, or pick apps and I'll do it for you.",
            Modifier.padding(horizontal = 20.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IdeaCard(Icons.Default.AutoAwesome, "Save my place now", onCapture)
            if (BuildConfig.DEMO_FIXTURES) IdeaCard(Icons.Default.AutoAwesome, "Replay the demo story (debug build)", onDemo)
            if (!watching) IdeaCard(Icons.Default.AutoAwesome, "Choose apps to watch", onChooseApps)
        }
    }
}

@Composable
private fun IdeaCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    GlassCard(
        Modifier.width(150.dp).height(124.dp),
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Ember.Glow, modifier = Modifier.size(18.dp))
        Spacer(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Ember.Cream)
    }
}

@Composable
private fun Notice(text: String, action: Pair<String, () -> Unit>?, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ember.Cream)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            action?.let { (label, f) -> EmberButton(label, f, compact = true) }
            GlassButton("Dismiss", dismiss)
        }
    }
}

/** The capture panel: type the next step and press the orb, or press the orb empty for the full form. */
@Composable
private fun CapturePanel(
    draft: String,
    onDraft: (String) -> Unit,
    useCase: UseCase,
    onUseCase: (UseCase) -> Unit,
    justSaved: Boolean,
    onDetails: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(32.dp), Ember.GlassStrong)
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
    ) {
        AnimatedVisibility(justSaved) {
            MascotSays("Saved. Your place is safe with me.", Modifier.padding(start = 8.dp, top = 8.dp), pose = MascotPose.Wave, size = 52.dp)
        }
        TextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = { Text("What were you about to do next?", style = MaterialTheme.typography.bodyLarge, color = Ember.Faint) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Ember.Glow,
                focusedTextColor = Ember.Cream,
                unfocusedTextColor = Ember.Cream,
            ),
        )
        Row(
            Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassIconButton(Icons.Default.Tune, "Add details", onDetails, fill = SoftGlass)
            UseCaseChip(useCase, onUseCase)
            Spacer(Modifier.weight(1f))
            OrbButton(if (draft.isBlank()) Icons.Default.Add else Icons.Default.ArrowUpward, "Save my place", onSave)
        }
    }
}

@Composable
private fun UseCaseChip(useCase: UseCase, onChange: (UseCase) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .glass(CircleShape, SoftGlass)
                .clickable(onClickLabel = "Change the kind of work") { open = true }
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(useCase.label(), style = MaterialTheme.typography.labelMedium, color = Ember.Cream)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Ember.Ash, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            UseCase.entries.forEach { u ->
                DropdownMenuItem(text = { Text(u.label()) }, onClick = { onChange(u); open = false })
            }
        }
    }
}

private fun UseCase.label() = name.lowercase().replaceFirstChar(Char::uppercase)
