package com.cueback.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cueback.app.AppContainer
import com.cueback.app.CueBackApp
import com.cueback.app.core.model.Fact
import com.cueback.app.core.model.Provenance
import com.cueback.app.ui.theme.NoteSerif
import com.cueback.app.ui.theme.Ribbon

@Composable
inline fun <reified T : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> T): T {
    val app = LocalContext.current.applicationContext as CueBackApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}

fun provenanceLabel(p: Provenance) = when (p) {
    Provenance.DETECTED -> "Detected"
    Provenance.INFERRED -> "Inferred"
    Provenance.USER -> "You said"
    Provenance.UNKNOWN -> "Unknown"
}

/** Provenance is spelled out in words, never signalled by color alone. */
@Composable
fun ProvenanceTag(p: Provenance, modifier: Modifier = Modifier) {
    val color = when (p) {
        Provenance.USER -> MaterialTheme.colorScheme.primary
        Provenance.DETECTED -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        provenanceLabel(p),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun FactBlock(label: String, fact: Fact?, modifier: Modifier = Modifier, emptyText: String? = null) {
    if (fact == null && emptyText == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fact?.let { ProvenanceTag(it.provenance) }
        }
        Text(fact?.text ?: emptyText!!, style = MaterialTheme.typography.bodyLarge, color = if (fact == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

/** The signature element: a bookmark ribbon marking the exact next action. */
@Composable
fun RibbonNext(fact: Fact?, modifier: Modifier = Modifier, large: Boolean = true) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Ribbon))
            Column(Modifier.padding(horizontal = 16.dp, vertical = if (large) 18.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    fact?.let { ProvenanceTag(it.provenance) }
                }
                Text(
                    fact?.text ?: "CueBack found where you stopped, but couldn't tell the next step.",
                    style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium.copy(fontFamily = NoteSerif),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String?, size: Int = 28, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CueBackApp
    val bmp = remember(packageName) {
        packageName?.takeIf { !it.startsWith("~") }?.let { app.container.catalog.iconOf(it) }?.toBitmap(96, 96)?.asImageBitmap()
    }
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = modifier.size(size.dp).clip(RoundedCornerShape(7.dp)))
    } else {
        Box(modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun ConfidenceText(confidence: Double, modifier: Modifier = Modifier) {
    val word = when {
        confidence >= 0.8 -> "High confidence"
        confidence >= 0.55 -> "Medium confidence"
        else -> "Low confidence"
    }
    Text(
        word,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = "$word, ${(confidence * 100).toInt()} percent" },
    )
}

fun relativeTime(then: Long?, now: Long = System.currentTimeMillis()): String {
    if (then == null) return ""
    val m = (now - then) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        m < 48 * 60 -> "yesterday"
        else -> "${m / (24 * 60)} days ago"
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
