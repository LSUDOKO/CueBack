package com.cueback.app.ui.capture

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.cueback.app.ui.components.Hint
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Save my place") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint(
                if (ui.liveSession) "CueBack is already tracking this session. Anything you add here joins what it observed, and the session is paused now."
                else "Only the next step matters. Everything else is optional.",
            )
            OutlinedTextField(
                value = next,
                onValueChange = { if (it.length <= 300) next = it },
                label = { Text("What were you about to do next?") },
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
                OutlinedTextField(title, { if (it.length <= 80) title = it }, label = { Text("Task") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            OutlinedTextField(goal, { if (it.length <= 200) goal = it }, label = { Text("Goal (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(done, { if (it.length <= 200) done = it }, label = { Text("What you got done (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(blocker, { if (it.length <= 200) blocker = it }, label = { Text("What's in the way (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(link, { if (it.length <= 2048) link = it }, label = { Text("Link to reopen (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = link.isNotBlank() && ArtifactPolicy.safeWebUrl(link) == null,
                supportingText = { if (link.isNotBlank() && ArtifactPolicy.safeWebUrl(link) == null) Text("Use an http or https link") })
            if (!ui.liveSession) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(UseCase.CODING, UseCase.STUDY, UseCase.WRITING, UseCase.RESEARCH).forEach { u ->
                        FilterChip(selected = useCase == u, onClick = { useCase = u }, label = { Text(u.name.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
            }
            if (ui.atLimit && !ui.liveSession) Hint("Free keeps 3 open contexts. Saving this moves your oldest one to the archive.")
            Button(
                onClick = { vm.save(title, goal, done, blocker, next, link, useCase, onBack) },
                enabled = ui.liveSession || title.isNotBlank() || next.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text("Save my place") }
        }
    }
}
