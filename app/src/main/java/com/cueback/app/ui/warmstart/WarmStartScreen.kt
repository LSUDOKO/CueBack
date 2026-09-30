package com.cueback.app.ui.warmstart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.billing.FeatureGate
import com.cueback.app.core.engine.DeltaBuilder
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.DeltaItem
import com.cueback.app.core.model.DeltaKind
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.data.repo.Metric
import com.cueback.app.data.repo.WarmStartSource
import com.cueback.app.detect.WarmStartView
import com.cueback.app.platform.LaunchResult
import com.cueback.app.ui.components.AppIcon
import com.cueback.app.ui.components.ConfidenceText
import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.FactBlock
import com.cueback.app.ui.components.GlassButton
import com.cueback.app.ui.components.GlassCard
import com.cueback.app.ui.components.GlassIconButton
import com.cueback.app.ui.components.GlassTextField
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.Mascot
import com.cueback.app.ui.components.MascotAvatar
import com.cueback.app.ui.components.MascotPose
import com.cueback.app.ui.components.RibbonNext
import com.cueback.app.ui.components.VSpace
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.components.glass
import com.cueback.app.ui.theme.Backdrop
import com.cueback.app.ui.theme.Ember
import com.cueback.app.ui.theme.EmberBackdrop
import com.cueback.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

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

@OptIn(ExperimentalLayoutApi::class)
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

    EmberBackdrop(Backdrop.Night) {
        when {
            ui.loading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Mascot(MascotPose.Think, size = 150.dp)
                VSpace(14)
                Text("Finding your place…", style = MaterialTheme.typography.labelMedium, color = Ember.Ash)
            }
            ui.missing -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Mascot(MascotPose.Side, size = 140.dp)
                VSpace(18)
                Text("This context no longer exists.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                VSpace(8)
                Text(
                    "It may have been deleted. Your other contexts are on the home screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ember.Ash,
                    textAlign = TextAlign.Center,
                )
                VSpace(22)
                EmberButton("Go to home", onClose)
            }
            ui.reentrySeconds != null -> ReentryResult(ui.reentrySeconds!!, reduced) {
                vm.consumeResult()
                // Show the paywall only after the user has experienced a real successful recovery.
                scope.launch { if (vm.shouldShowPaywallAfterSuccess()) onPaywallAfterSuccess() else onClose() }
            }
            else -> {
                val v = ui.view!!
                var controlsHeight by remember { mutableStateOf(0) }
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ClosePill(onClose, Modifier.align(Alignment.Center))
                        GlassIconButton(
                            Icons.AutoMirrored.Filled.List,
                            "Context details",
                            { onOpenContext(contextId) },
                            Modifier.align(Alignment.CenterEnd),
                        )
                    }
                    // The list runs underneath the controls and fades out, instead of being cut off above them.
                    Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Staggered(0, reduced) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                VSpace(8)
                                Mascot(if (ui.accepted) MascotPose.Turn else MascotPose.Wave, size = 148.dp)
                                VSpace(10)
                                Text(
                                    if (ui.accepted) "Cue is timing your return" else "Cue found your place",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Ember.Ash,
                                )
                                VSpace(14)
                                Text(
                                    "Welcome back.",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Ember.Cream,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    "You were on “${v.context.title.text}”.",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Ember.Faint,
                                    textAlign = TextAlign.Center,
                                )
                                VSpace(10)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(v.context.primaryApp, size = 18)
                                    Text("Paused ${DeltaBuilder.formatDuration(v.warmStart.awayMs)} ago", style = MaterialTheme.typography.labelMedium, color = Ember.Ash)
                                    ConfidenceText(v.warmStart.matchScore)
                                    if (v.context.isDemo) Text("Demo replay", style = MaterialTheme.typography.labelSmall, color = Ember.Glow)
                                }
                            }
                        }
                        // The next action leads, so it is on screen without scrolling even on small phones; history supports it below.
                        Staggered(1, reduced) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                RibbonNext(v.context.nextAction)
                                if (v.context.needsIntent) IntentInput(vm::setNext)
                            }
                        }
                        Sections(v, reduced, vm::openArtifact)
                        ui.message?.let { Hint(it) }
                        Spacer(Modifier.height(with(LocalDensity.current) { controlsHeight.toDp() }))
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { controlsHeight = it.height }
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.17f to Ember.Coal.copy(alpha = 0.96f),
                                    0.30f to Ember.Coal,
                                    1f to Ember.Coal,
                                ),
                            )
                            .navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!ui.accepted) {
                            EmberButton("Resume where I left off", vm::accept, Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (v.warmStart.level != RecoveryLevel.FULL) {
                                    GlassButton(
                                        if (v.fullLocked) "Full context (Pro)" else "Full context",
                                        { vm.showFull(onPaywall) },
                                        Modifier.weight(1f),
                                    )
                                }
                                GlassButton("Not this task", { vm.reject(onClose) }, Modifier.weight(1f), contentColor = Ember.Ash)
                            }
                        } else {
                            Text("Measuring your re-entry", style = MaterialTheme.typography.titleMedium, color = Ember.Cream)
                            Hint("CueBack notes the moment you've been back in this task's app for 15 seconds. Nothing is recorded but that timestamp.")
                            GlassButton("I'm back on track", vm::backOnTrack, Modifier.fillMaxWidth())
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosePill(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(CircleShape, Color(0x24FFF3E8))
            .clickable(role = Role.Button, onClick = onClose)
            .semantics { contentDescription = "Close warm start" }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Close, contentDescription = null, tint = Ember.Cream, modifier = Modifier.size(16.dp))
        Text("Close", style = MaterialTheme.typography.labelMedium, color = Ember.Cream)
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                c.notes.forEach { FactBlock("Note", it) }
                if (c.artifacts.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("Artifacts", style = MaterialTheme.typography.labelMedium, color = Ember.Ash)
                        c.artifacts.takeLast(6).reversed().forEach { a ->
                            Row(
                                Modifier.fillMaxWidth().clickable { open(a) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AppIcon(a.sourcePackage, size = 20)
                                Text(a.title ?: a.locator, style = MaterialTheme.typography.bodyMedium, color = Ember.Cream, modifier = Modifier.weight(1f), maxLines = 2)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", tint = Ember.Glow, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                c.anchor?.let { Hint("Last known place: ${it.title ?: it.locator}") }
            }
        }
        Staggered(5, reduced) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Evidence", style = MaterialTheme.typography.labelMedium, color = Ember.Ash)
                c.evidence.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember.Ash) }
            }
        }
    }
}

/** What changed while you were away, told by Cue. */
@Composable
fun DeltaList(delta: List<DeltaItem>) {
    if (delta.isEmpty()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        MascotAvatar(size = 26.dp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f, fill = false)
                .glass(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("While you were away", style = MaterialTheme.typography.labelMedium, color = Ember.Ash)
            delta.forEach { d ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Time away and interruptions are plain facts; only state that could have changed gets a Same/Changed tag.
                    if (d.kind != DeltaKind.TIME_AWAY && d.kind != DeltaKind.INTERRUPTIONS) {
                        Text(
                            if (d.changed) "Changed" else "Same",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (d.changed) MaterialTheme.colorScheme.error else Ember.Glow,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(d.text, style = MaterialTheme.typography.bodyMedium, color = Ember.Cream)
                }
            }
        }
    }
}

@Composable
private fun IntentInput(onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassTextField(
            value = text,
            onValueChange = { if (it.length <= 300) text = it },
            label = "What were you about to do next?",
            modifier = Modifier.fillMaxWidth(),
        )
        GlassButton("Save next step", { onSave(text); text = "" }, enabled = text.isNotBlank())
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
private fun ReentryResult(seconds: Long, reduced: Boolean, onDone: () -> Unit) {
    // The one celebration in the app: a ring and a scatter of sparks leave the owl once, then it is quiet.
    val burst = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(
            MascotPose.Wave,
            size = 190.dp,
            modifier = Modifier.drawBehind {
                val p = burst.value
                if (p >= 1f) return@drawBehind
                val r = size.minDimension
                drawCircle(Ember.Flame.copy(alpha = (1f - p) * 0.7f), radius = r * (0.45f + 0.65f * p), style = Stroke(width = 2.dp.toPx()))
                repeat(14) { i ->
                    val angle = (i / 14f) * 6.2832f + 0.3f
                    val reach = r * (0.5f + 0.75f * p) * (if (i % 2 == 0) 1f else 0.82f)
                    drawCircle(
                        if (i % 3 == 0) Ember.Core else Ember.Glow,
                        radius = (4.dp.toPx()) * (1f - p) + 1f,
                        center = center + Offset(cos(angle) * reach, sin(angle) * reach),
                        alpha = 1f - p,
                    )
                }
            },
        )
        VSpace(22)
        Text("Back in ${formatSeconds(seconds)}.", style = MaterialTheme.typography.displaySmall, color = Ember.Cream, textAlign = TextAlign.Center)
        VSpace(12)
        Text(
            "That's the time from your warm start to the first stretch of real work in this task. It's yours to keep — not a score.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ember.Ash,
            textAlign = TextAlign.Center,
        )
        VSpace(28)
        EmberButton("Done", onDone)
    }
}

fun formatSeconds(s: Long) = if (s < 90) "$s seconds" else "${s / 60} min ${s % 60} s"

/** Compact form for lists: "42s", "3 min", "1 h 5 min". */
fun formatSecondsShort(s: Long) = when {
    s < 90 -> "${s}s"
    s < 3600 -> "${(s + 30) / 60} min"
    else -> "${s / 3600} h ${(s % 3600) / 60} min"
}
