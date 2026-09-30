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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import com.cueback.app.ui.theme.Ember

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

/** Provenance is spelled out in words, never signalled by color or position alone. */
@Composable
fun ProvenanceTag(p: Provenance, modifier: Modifier = Modifier, onFlame: Boolean = false) {
    val color = when {
        onFlame -> Ember.Ink
        p == Provenance.USER -> Ember.Glow
        p == Provenance.DETECTED -> Ember.Cream
        else -> Ember.Ash
    }
    Text(
        provenanceLabel(p),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * A fact, laid out as a line in a conversation. What you said sits on the right in flame; what Cue
 * detected or inferred sits on the left, next to the owl. The provenance tag still says which in words.
 */
@Composable
fun FactBlock(label: String, fact: Fact?, modifier: Modifier = Modifier, emptyText: String? = null) {
    if (fact == null && emptyText == null) return
    val mine = fact?.provenance == Provenance.USER
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            MascotAvatar(size = 26.dp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
        }
        val shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomEnd = if (mine) 6.dp else 20.dp,
            bottomStart = if (mine) 20.dp else 6.dp,
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = 320.dp)
                .then(
                    if (mine) Modifier.background(Brush.verticalGradient(listOf(Ember.Glow, Ember.Flame)), shape)
                    else Modifier.glass(shape),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (mine) Ember.Ink.copy(alpha = 0.72f) else Ember.Ash)
                fact?.let { ProvenanceTag(it.provenance, onFlame = mine) }
            }
            Text(
                fact?.text ?: emptyText!!,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    mine -> Ember.Ink
                    fact == null -> Ember.Ash
                    else -> Ember.Cream
                },
            )
        }
    }
}

/** The signature element: a bookmark ribbon marking the exact next action. */
@Composable
fun RibbonNext(fact: Fact?, modifier: Modifier = Modifier, large: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.large, Ember.GlassStrong)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(Ember.Core, Ember.Flame))))
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = if (large) 18.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Next", style = MaterialTheme.typography.labelLarge, color = Ember.Glow)
                fact?.let { ProvenanceTag(it.provenance) }
            }
            Text(
                fact?.text ?: "CueBack found where you stopped, but couldn't tell the next step.",
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                color = if (fact == null) Ember.Ash else Ember.Cream,
            )
        }
    }
}

@Composable
fun AppIcon(packageName: String?, modifier: Modifier = Modifier, size: Int = 28) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CueBackApp
    val bmp = remember(packageName) {
        packageName?.takeIf { !it.startsWith("~") }?.let { app.container.catalog.iconOf(it) }?.toBitmap(96, 96)?.asImageBitmap()
    }
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)))
    } else {
        // No app behind this place (saved by hand, or the app is gone): mark it with the bookmark.
        Box(modifier.size(size.dp).clip(CircleShape).background(Color(0x29FFF3E8)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Ember.Glow, modifier = Modifier.size((size * 0.6f).dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Ember.Cream,
        modifier = modifier.padding(top = 10.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ember.Ash, modifier = modifier)
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
        color = Ember.Ash,
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
