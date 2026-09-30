package com.cueback.app.ui.capture

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.billing.FeatureGate
import com.cueback.app.billing.ProFeature
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.NoteKind
import com.cueback.app.core.model.UseCase
import com.cueback.app.core.model.WorkEvent
import com.cueback.app.platform.ArtifactPolicy
import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.EmberChip
import com.cueback.app.ui.components.EmberScaffold
import com.cueback.app.ui.components.GlassTextField
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.MascotPose
import com.cueback.app.ui.components.MascotSays
import com.cueback.app.ui.components.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptureUi(val liveSession: Boolean = false, val voiceAllowed: Boolean = false, val useCase: UseCase = UseCase.GENERAL, val atLimit: Boolean = false)

class CaptureViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CaptureUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val gate = FeatureGate(c.billing.state.value)
            _ui.value = CaptureUi(
                liveSession = c.detection.openSessionActive(),
                voiceAllowed = gate.allows(ProFeature.VOICE_CAPTURE),
                useCase = c.settings.current().useCase,
                atLimit = !gate.canCreateContext(c.contexts.openCount()),
            )
        }
    }

    /**
     * With a live session, the notes join the automatically observed evidence and the session is
     * paused explicitly — the capsule is built by the engine. Otherwise a manual context is created.
     */
    fun save(title: String, goal: String, done: String, blocker: String, next: String, link: String, useCase: UseCase, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = c.clock()
            val url = ArtifactPolicy.safeWebUrl(link)
            val artifact = url?.let { Artifact(ArtifactType.URL, it, null, null, now) }
            if (_ui.value.liveSession) {
                val notes = listOf(NoteKind.GOAL to goal.ifBlank { title }, NoteKind.DONE to done, NoteKind.BLOCKER to blocker, NoteKind.NEXT to next)
                    .filter { it.second.isNotBlank() }
                    .mapIndexed { i, (k, t) -> WorkEvent(now - 50 + i, EventType.MANUAL_NOTE, noteKind = k, note = t.trim()) }
                val share = artifact?.let { listOf(WorkEvent(now - 10, EventType.SHARE_RECEIVED, artifact = it)) }.orEmpty()
                c.detection.runPass(notes + share + WorkEvent(now, EventType.EXPLICIT_PAUSE))
            } else {
                val capsule = c.builder.manual(c.newId(), now, title, goal.ifBlank { null }, done.ifBlank { null }, blocker.ifBlank { null }, next.ifBlank { null }, artifact, useCase)
                c.detection.saveManual(capsule)
            }
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureScreen(onBack: () -> Unit, onPaywall: () -> Unit) {
    val vm = containerViewModel { CaptureViewModel(it) }
    val ui by vm.ui.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var done by rememberSaveable { mutableStateOf("") }
    var blocker by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var useCase by remember { mutableStateOf(ui.useCase) }
    LaunchedEffect(ui.useCase) { useCase = ui.useCase }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { next = it.take(300) }
        }
    }

    EmberScaffold(title = "Save my place", onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MascotSays(
                if (ui.liveSession) "I'm already tracking this session. Anything you add here joins what I observed, and the session is paused now."
                else "Only the next step matters. Everything else is optional.",
                pose = MascotPose.Think,
                size = 72.dp,
            )
            GlassTextField(
                value = next,
                onValueChange = { if (it.length <= 300) next = it },
                label = "What were you about to do next?",
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        if (!ui.voiceAllowed) onPaywall() else speech.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "What were you about to do next?")
                                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
                        )
                    }) { Icon(Icons.Default.Mic, contentDescription = if (ui.voiceAllowed) "Speak the next step" else "Voice capture (Pro)") }
                },
            )
            if (!ui.liveSession) {
                GlassTextField(title, { if (it.length <= 80) title = it }, label = "Task", modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            GlassTextField(goal, { if (it.length <= 200) goal = it }, label = "Goal (optional)", modifier = Modifier.fillMaxWidth())
            GlassTextField(done, { if (it.length <= 200) done = it }, label = "What you got done (optional)", modifier = Modifier.fillMaxWidth())
            GlassTextField(blocker, { if (it.length <= 200) blocker = it }, label = "What's in the way (optional)", modifier = Modifier.fillMaxWidth())
            val badLink = link.isNotBlank() && ArtifactPolicy.safeWebUrl(link) == null
            GlassTextField(
                link,
                { if (it.length <= 2048) link = it },
                label = "Link to reopen (optional)",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = badLink,
                supportingText = if (badLink) "Use an http or https link" else null,
            )
            if (!ui.liveSession) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(UseCase.CODING, UseCase.STUDY, UseCase.WRITING, UseCase.RESEARCH).forEach { u ->
                        EmberChip(u.name.lowercase().replaceFirstChar(Char::uppercase), selected = useCase == u, onClick = { useCase = u }, single = true)
                    }
                }
            }
            if (ui.atLimit && !ui.liveSession) Hint("Free keeps 3 open contexts. Saving this moves your oldest one to the archive.")
            EmberButton(
                "Save my place",
                onClick = { vm.save(title, goal, done, blocker, next, link, useCase, onBack) },
                enabled = ui.liveSession || title.isNotBlank() || next.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
            )
        }
    }
}
