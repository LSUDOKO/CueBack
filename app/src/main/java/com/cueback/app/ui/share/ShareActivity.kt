package com.cueback.app.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.cueback.app.CueBackApp
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.NoteKind
import com.cueback.app.platform.ArtifactPolicy
import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.GlassButton
import com.cueback.app.ui.components.GlassTextField
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.MascotAvatar
import com.cueback.app.ui.theme.Ember
import com.cueback.app.ui.theme.CueBackTheme
import kotlinx.coroutines.launch

/** Parses an ACTION_SEND intent into an artifact. Pure-ish so the rules are testable. */
object ShareParser {
    fun parse(text: String?, subject: String?, streamUri: String?, streamName: String?, source: String?, now: Long): Artifact? {
        if (streamUri != null && streamUri.startsWith("content://")) {
            return Artifact(ArtifactType.FILE, streamUri, streamName ?: subject, source, now)
        }
        val t = text?.trim()?.take(4000) ?: return null
        if (t.isEmpty()) return null
        val url = ArtifactPolicy.firstUrl(t)
        return if (url != null) {
            val title = subject?.trim()?.takeIf { it.isNotEmpty() } ?: t.replace(url, "").trim().takeIf { it.isNotEmpty() }
            Artifact(ArtifactType.URL, url, title?.take(200), source, now)
        } else {
            Artifact(ArtifactType.TEXT, t, subject?.take(200) ?: t.take(60), source, now)
        }
    }
}

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CueBackApp).container
        val source = referrer?.takeIf { it.scheme == "android-app" }?.host
        val stream = intentStream(intent)
        val artifact = ShareParser.parse(
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            stream?.toString(),
            stream?.let(::displayName),
            source,
            container.clock(),
        )
        if (artifact == null) { finish(); return }

        setContent {
            CueBackTheme {
                val open by produceState(emptyList<ContextCapsule>()) { value = container.contexts.open() }
                val live by produceState(false) { value = container.detection.openSessionActive() }
                var target by remember { mutableStateOf<String?>(null) }
                var next by remember { mutableStateOf("") }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = Ember.Cream,
                    border = BorderStroke(1.dp, Ember.GlassStrokeTop),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MascotAvatar(size = 36.dp)
                            Text("Save to CueBack", style = MaterialTheme.typography.titleLarge)
                        }
                        Hint(artifact.title ?: artifact.locator)
                        Option(if (live) "Current work session" else "A new place to resume", target == null) { target = null }
                        open.take(6).forEach { c -> Option(c.title.text, target == c.id) { target = c.id } }
                        GlassTextField(next, { next = it.take(300) }, label = "Next step (optional)", modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton("Cancel", { finish() })
                            EmberButton("Save", {
                                lifecycleScope.launch {
                                    container.detection.addShared(artifact, target)
                                    if (next.isNotBlank()) container.detection.addNote(target, NoteKind.NEXT, next)
                                    finish()
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    private fun intentStream(i: Intent): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
