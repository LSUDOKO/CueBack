package com.cueback.app.ui.warmstart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.billing.FeatureGate
import com.cueback.app.core.engine.DeltaBuilder
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.DeltaItem
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.data.repo.Metric
import com.cueback.app.data.repo.WarmStartSource
import com.cueback.app.detect.WarmStartView
import com.cueback.app.platform.LaunchResult
import com.cueback.app.ui.components.AppIcon
import com.cueback.app.ui.components.ConfidenceText
import com.cueback.app.ui.components.FactBlock
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.RibbonNext
import com.cueback.app.ui.components.VSpace
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarmStartUi(
    val loading: Boolean = true,
    val view: WarmStartView? = null,
    val sessionId: String? = null,
    val accepted: Boolean = false,
    val message: String? = null,
    val reentrySeconds: Long? = null,
    val missing: Boolean = false,
)

class WarmStartViewModel(private val c: AppContainer, private val contextId: String, private val source: WarmStartSource) : ViewModel() {
    private val _ui = MutableStateFlow(WarmStartUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val v = c.detection.warmStartView(contextId)
            if (v == null) { _ui.value = WarmStartUi(loading = false, missing = true); return@launch }
            val sid = c.detection.onWarmStartShown(contextId, v.warmStart.level, source)
            _ui.value = WarmStartUi(loading = false, view = v, sessionId = sid)
        }
        viewModelScope.launch {
            c.detection.completedReentry.collect { rec ->
                if (rec != null && rec.session.id == _ui.value.sessionId) {
                    _ui.update { it.copy(reentrySeconds = (rec.session.reentryMs ?: 0) / 1000) }
                }
            }
        }
    }

    fun showFull(onLocked: () -> Unit) {
        val cur = _ui.value.view ?: return
        if (cur.fullLocked) { onLocked(); return }
        viewModelScope.launch {
            c.detection.warmStartView(contextId, explicitFull = true)?.let { v -> _ui.update { it.copy(view = v) } }
        }
    }

    fun accept() {
        val s = _ui.value
        val sid = s.sessionId ?: return
        val ctx = s.view?.context ?: return
        viewModelScope.launch {
            c.detection.onWarmStartAccepted(sid, contextId)
            val target = ctx.anchor ?: ctx.primaryApp?.let { Artifact(com.cueback.app.core.model.ArtifactType.APP, it, null, it) }
            val msg = when (val r = target?.let(c.launcher::launch)) {
                LaunchResult.Opened -> { c.analytics.track(Metric.ARTIFACT_OPENED); null }
                is LaunchResult.Unavailable -> r.reason
                null -> "Nothing to open for this context. Pick up from the next action."
            }
            _ui.update { it.copy(accepted = true, message = msg) }
        }
    }

    fun openArtifact(a: Artifact) {
        when (val r = c.launcher.launch(a)) {
            LaunchResult.Opened -> viewModelScope.launch { c.analytics.track(Metric.ARTIFACT_OPENED) }
            is LaunchResult.Unavailable -> _ui.update { it.copy(message = r.reason) }
        }
    }

    fun backOnTrack() {
        val sid = _ui.value.sessionId ?: return
        viewModelScope.launch { c.detection.markBackOnTrack(sid) }
    }

    fun reject(done: () -> Unit) {
        viewModelScope.launch {
            c.detection.onWarmStartRejected(_ui.value.sessionId, contextId)
            done()
        }
    }

    fun setNext(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            c.contexts.setNextAction(contextId, text)
            c.analytics.track(Metric.INTENT_CAPTURED)
            c.detection.warmStartView(contextId)?.let { v -> _ui.update { it.copy(view = v) } }
        }
    }

    suspend fun shouldShowPaywallAfterSuccess(): Boolean {
        val s = c.settings.current()
        val pro = FeatureGate(c.billing.state.value).pro
        if (pro || s.hasSeenPaywall || !c.billing.configured) return false
        c.settings.update { it.copy(hasSeenPaywall = true) }
        return true
    }

    fun consumeResult() = c.detection.consumeCompletedReentry()
}

@Composable
fun WarmStartScreen(
    contextId: String,
    source: WarmStartSource,
    onClose: () -> Unit,
    onPaywall: () -> Unit,
    onOpenContext: (String) -> Unit,
    onPaywallAfterSuccess: () -> Unit = onPaywall,
) {
    val vm = containerViewModel(key = "warm_$contextId") { WarmStartViewModel(it, contextId, source) }
    val ui by vm.ui.collectAsState()
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.missing -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("This context no longer exists.", style = MaterialTheme.typography.titleLarge)
                Hint("It may have been deleted. Your other contexts are on the home screen.")
                VSpace(16)
                Button(onClick = onClose) { Text("Go to home") }
            }
            ui.reentrySeconds != null -> ReentryResult(ui.reentrySeconds!!) {
                vm.consumeResult()
                // Show the paywall only after the user has experienced a real successful recovery.
                scope.launch { if (vm.shouldShowPaywallAfterSuccess()) onPaywallAfterSuccess() else onClose() }
            }
            else -> {
                val v = ui.view!!
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close warm start") }
                        Box(Modifier.weight(1f))
                        TextButton(onClick = { onOpenContext(contextId) }) { Text("Context details") }
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Staggered(0, reduced) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Welcome back.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                                Text("You were on ${v.context.title.text}.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AppIcon(v.context.primaryApp, 20)
                                    Text("Paused ${DeltaBuilder.formatDuration(v.warmStart.awayMs)} ago", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ConfidenceText(v.warmStart.matchScore)
                                    if (v.context.isDemo) Text("Demo replay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Sections(v, reduced, vm::openArtifact)
                        Staggered(6, reduced) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                RibbonNext(v.context.nextAction)
                                if (v.context.needsIntent) IntentInput(vm::setNext)
                            }
                        }
                        ui.message?.let { Hint(it) }
                        VSpace(8)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!ui.accepted) {
                            Button(onClick = vm::accept, modifier = Modifier.fillMaxWidth()) {
                                Text("Resume where I left off", modifier = Modifier.padding(vertical = 6.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (v.warmStart.level != RecoveryLevel.FULL) {
                                    OutlinedButton(onClick = { vm.showFull(onPaywall) }, modifier = Modifier.weight(1f)) {
                                        Text(if (v.fullLocked) "Full context (Pro)" else "Show full context")
                                    }
                                }
                                TextButton(onClick = { vm.reject(onClose) }, modifier = Modifier.weight(1f)) { Text("Not this task") }
                            }
                        } else {
                            Text("Measuring your re-entry", style = MaterialTheme.typography.titleMedium)
                            Hint("CueBack notes the moment you've been back in this task's app for 15 seconds. Nothing is recorded but that timestamp.")
                            OutlinedButton(onClick = vm::backOnTrack, modifier = Modifier.fillMaxWidth()) { Text("I'm back on track") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sections(v: WarmStartView, reduced: Boolean, open: (Artifact) -> Unit) {
    val c = v.context
    val level = v.warmStart.level
    if (level == RecoveryLevel.MICRO) return
    if (level == RecoveryLevel.FULL) {
        Staggered(1, reduced) { FactBlock("Goal", c.goal, emptyText = "No goal recorded") }
    }
    Staggered(2, reduced) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val had = c.completed.lastOrNull() ?: c.currentState
            FactBlock("You had", had)
            if (level == RecoveryLevel.FULL && c.completed.size > 1) {
                c.completed.dropLast(1).forEach { FactBlock("Also done", it) }
            }
            if (level == RecoveryLevel.FULL && c.currentState != null && had != c.currentState) FactBlock("Current state", c.currentState)
            FactBlock("Blocker", c.blocker)
        }
    }
    Staggered(3, reduced) { DeltaList(v.warmStart.delta) }
    if (level == RecoveryLevel.FULL) {
        Staggered(4, reduced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                c.notes.forEach { FactBlock("Note", it) }
                if (c.artifacts.isNotEmpty()) {
                    Text("Artifacts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    c.artifacts.takeLast(6).reversed().forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open(a) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppIcon(a.sourcePackage, 20)
                            Text(a.title ?: a.locator, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", Modifier.padding(start = 4.dp))
                        }
                    }
                }
                c.anchor?.let { Hint("Last known place: ${it.title ?: it.locator}") }
            }
        }
        Staggered(5, reduced) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Evidence", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.evidence.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun DeltaList(delta: List<DeltaItem>) {
    if (delta.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("While you were away", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        delta.forEach { d ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (d.changed) "Changed" else "Same", style = MaterialTheme.typography.labelSmall,
                    color = if (d.changed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp))
                Text(d.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun IntentInput(onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 300) text = it },
            label = { Text("What were you about to do next?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
        TextButton(onClick = { onSave(text); text = "" }, enabled = text.isNotBlank()) { Text("Save next step") }
    }
}

/** One orchestrated reveal: sections arrive in order, total under ~600 ms; off with reduced motion. */
@Composable
private fun Staggered(index: Int, reduced: Boolean, content: @Composable () -> Unit) {
    if (reduced) { content(); return }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260, delayMillis = index * 55)) + slideInVertically(tween(320, delayMillis = index * 55)) { it / 6 },
    ) { content() }
}

@Composable
private fun ReentryResult(seconds: Long, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Back in ${formatSeconds(seconds)}.", style = MaterialTheme.typography.displaySmall)
        VSpace(12)
        Hint("That's the time from your warm start to the first stretch of real work in this task. It's yours to keep — not a score.")
        VSpace(28)
        Button(onClick = onDone) { Text("Done") }
    }
}

fun formatSeconds(s: Long) = if (s < 90) "$s seconds" else "${s / 60} min ${s % 60} s"
