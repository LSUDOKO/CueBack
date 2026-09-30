package com.cueback.app.ui.detail

import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.EmberScaffold
import com.cueback.app.ui.components.GlassButton
import com.cueback.app.ui.components.GlassCard
import com.cueback.app.ui.components.GlassTextField
import com.cueback.app.ui.theme.Ember
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.billing.FeatureGate
import com.cueback.app.billing.ProFeature
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.data.db.TimelineEntity
import com.cueback.app.data.repo.ReentryOutcome
import com.cueback.app.data.repo.ReentryRecord
import com.cueback.app.platform.LaunchResult
import com.cueback.app.ui.components.AppIcon
import com.cueback.app.ui.components.ConfidenceText
import com.cueback.app.ui.components.FactBlock
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.RibbonNext
import com.cueback.app.ui.components.SectionTitle
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.components.relativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUi(
    val context: ContextCapsule? = null,
    val timeline: List<TimelineEntity> = emptyList(),
    val reentries: List<ReentryRecord> = emptyList(),
    val timelineAllowed: Boolean = false,
    val aiReady: Boolean = false,
    val message: String? = null,
    val loaded: Boolean = false,
)

class ContextDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val ui: StateFlow<DetailUi> = combine(
        c.contexts.observe(id), c.contexts.observeTimeline(id), c.reentry.observeFor(id), c.billing.state,
        combine(message, c.settings.settings) { m, s -> m to c.ai.isReady(s) },
    ) { ctx, tl, re, ent, (msg, ai) ->
        DetailUi(ctx, tl, re, FeatureGate(ent).allows(ProFeature.CONTEXT_TIMELINE), ai, msg, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi())

    fun open(a: Artifact) {
        (c.launcher.launch(a) as? LaunchResult.Unavailable)?.let { message.value = it.reason }
    }

    fun setNext(text: String) = viewModelScope.launch { c.contexts.setNextAction(id, text) }
    fun complete() = viewModelScope.launch { c.contexts.setStatus(id, ContextStatus.COMPLETED); c.notifier.cancelFor(id) }
    fun reopen() = viewModelScope.launch {
        val gate = FeatureGate(c.billing.state.value)
        if (!gate.canCreateContext(c.contexts.openCount())) message.value = "Free keeps 3 open contexts. Complete one first, or upgrade to Pro."
        else c.contexts.setStatus(id, ContextStatus.PAUSED)
    }
    fun mute(m: Boolean) = viewModelScope.launch { c.contexts.setMuted(id, m) }
    fun delete(done: () -> Unit) = viewModelScope.launch { c.notifier.cancelFor(id); c.contexts.delete(id); done() }

    fun refineWithAi() = viewModelScope.launch {
        val ctx = c.contexts.get(id) ?: return@launch
        message.value = "Asking your AI provider…"
        c.ai.refine(ctx, c.settings.current(), c.catalog::labelOf)
            .onSuccess { s -> c.contexts.update(id, com.cueback.app.data.repo.TimelineKind.EDITED, "Refined with cloud AI") { c.ai.apply(it, s) }; message.value = "Refined. AI suggestions are marked Inferred." }
            .onFailure { message.value = "AI refinement failed: ${it.message}. Your context is unchanged." }
    }
}

@Composable
fun ContextDetailScreen(id: String, onBack: () -> Unit, onWarmStart: (String) -> Unit, onPaywall: () -> Unit) {
    val vm = containerViewModel(key = "detail_$id") { ContextDetailViewModel(it, id) }
    val ui by vm.ui.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var editNext by remember { mutableStateOf(false) }

    EmberScaffold(title = "Context", onBack = onBack) { pad ->
        val c = ui.context
        if (c == null) {
            Column(Modifier.padding(pad).padding(20.dp)) { if (ui.loaded) Hint("This context was deleted.") }
            return@EmberScaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(c.primaryApp, size = 30)
                Column(Modifier.weight(1f)) {
                    Text(c.title.text, style = MaterialTheme.typography.headlineSmall, color = Ember.Cream)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Hint(statusText(c))
                        ConfidenceText(c.confidence)
                    }
                }
            }
            RibbonNext(c.nextAction, large = false)
            if (editNext) NextEditor(c.nextAction?.text.orEmpty()) { vm.setNext(it); editNext = false }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.status == ContextStatus.PAUSED || c.status == ContextStatus.ACTIVE) {
                    EmberButton("Resume", { onWarmStart(c.id) }, Modifier.weight(1f))
                }
                if (!editNext) GlassButton(if (c.nextAction == null) "Add next step" else "Change next step", { editNext = true }, Modifier.weight(1f))
            }
            FactBlock("Goal", c.goal)
            FactBlock("Where it stood", c.currentState)
            c.completed.forEach { FactBlock("Done", it) }
            FactBlock("Blocker", c.blocker)
            c.notes.forEach { FactBlock("Note", it) }

            if (c.artifacts.isNotEmpty()) {
                SectionTitle("Artifacts")
                GlassCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    c.artifacts.reversed().forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.open(a) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppIcon(a.sourcePackage, size = 20)
                            Column(Modifier.weight(1f)) {
                                Text(a.title ?: a.locator, style = MaterialTheme.typography.bodyMedium, color = Ember.Cream, maxLines = 2)
                                if (a.title != null) Text(a.locator, style = MaterialTheme.typography.bodySmall, color = Ember.Ash, maxLines = 1)
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", tint = Ember.Glow, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            ui.message?.let { Hint(it) }

            SectionTitle("Re-entries")
            val done = ui.reentries.filter { it.outcome == ReentryOutcome.COMPLETED }
            if (done.isEmpty()) Hint("No measured re-entries yet.")
            done.forEach { r -> Hint("${relativeTime(r.session.warmStartAt)}: back in ${(r.session.reentryMs ?: 0) / 1000}s (${r.session.level.name.lowercase()} warm start)") }

            SectionTitle("Timeline")
            if (ui.timelineAllowed) {
                ui.timeline.take(30).forEach { t -> Hint("${relativeTime(t.at)} — ${t.summary}") }
            } else {
                Hint("See how this context evolved across sessions with Pro.")
                GlassButton("See Pro", onPaywall)
            }

            SectionTitle("Evidence")
            c.evidence.takeLast(12).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember.Ash) }

            HorizontalDivider(color = Ember.GlassStrokeTop)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reminders for this context", style = MaterialTheme.typography.bodyLarge, color = Ember.Cream, modifier = Modifier.weight(1f))
                Switch(checked = !c.muted, onCheckedChange = { vm.mute(!it) })
            }
            if (ui.aiReady) GlassButton("Refine with cloud AI", vm::refineWithAi, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.status == ContextStatus.COMPLETED || c.status == ContextStatus.ARCHIVED) {
                    GlassButton("Reopen", vm::reopen, Modifier.weight(1f))
                } else {
                    GlassButton("Mark done", vm::complete, Modifier.weight(1f))
                }
                GlassButton("Delete", { confirmDelete = true }, Modifier.weight(1f), contentColor = MaterialTheme.colorScheme.error)
            }
            Column(Modifier.padding(bottom = 32.dp)) {}
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this context?") },
            text = { Text("Its notes, artifacts, and re-entry history are removed from this device.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

private fun statusText(c: ContextCapsule) = when (c.status) {
    ContextStatus.ACTIVE -> "In progress"
    ContextStatus.PAUSED -> "Paused ${relativeTime(c.pausedAt)}"
    ContextStatus.COMPLETED -> "Done"
    ContextStatus.ARCHIVED -> "Archived"
}

@Composable
private fun NextEditor(initial: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassTextField(text, { if (it.length <= 300) text = it }, label = "What were you about to do next?", modifier = Modifier.fillMaxWidth())
        GlassButton("Save next step", { onSave(text) }, enabled = text.isNotBlank())
    }
}
