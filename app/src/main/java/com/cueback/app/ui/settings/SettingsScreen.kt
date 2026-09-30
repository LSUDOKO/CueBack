package com.cueback.app.ui.settings

import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.EmberChip
import com.cueback.app.ui.components.EmberScaffold
import com.cueback.app.ui.components.GlassButton
import com.cueback.app.ui.components.GlassCard
import com.cueback.app.ui.components.GlassTextField
import com.cueback.app.ui.components.MascotPose
import com.cueback.app.ui.components.MascotSays
import com.cueback.app.ui.theme.Ember
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cueback.app.BuildConfig
import com.cueback.app.billing.EntitlementState
import com.cueback.app.billing.FeatureGate
import com.cueback.app.core.engine.DepthPreference
import com.cueback.app.ui.components.AppIcon
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.SectionTitle
import com.cueback.app.ui.components.containerViewModel

const val PRIVACY_POLICY_URL = "https://github.com/LSUDOKO/CueBack/blob/main/PRIVACY_POLICY.md"

/** A settings group: related rows on one sheet of glass, separated by hairlines. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
private fun Rule() = HorizontalDivider(color = Color(0x1AFFF3E8))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onApps: () -> Unit, onPaywall: () -> Unit, onDeleted: () -> Unit, onDemo: (String) -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val ent by vm.entitlement.collectAsState()
    val usageOk by vm.usageGranted.collectAsState()
    val message by vm.message.collectAsState()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { vm.refreshPermissions(); onPauseOrDispose { } }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.consumeMessage() } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(ctx, it) } }
    val gate = FeatureGate(ent)

    EmberScaffold(title = "Settings", onBack = onBack, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionTitle("Detection") }
            item {
                Group {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Usage access", style = MaterialTheme.typography.bodyLarge, color = Ember.Cream)
                            Hint(if (usageOk) "Granted. CueBack reads which app is in front and when the screen locks." else "Needed to notice pauses and returns automatically.")
                        }
                        GlassButton(if (usageOk) "Manage" else "Allow", { ctx.startActivity(vm.usageAccessIntent(ctx)) })
                    }
                    Rule()
                    Row(Modifier.fillMaxWidth().clickable(onClick = onApps).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Apps to watch", style = MaterialTheme.typography.bodyLarge, color = Ember.Cream)
                            Hint(if (s.trackedApps.isEmpty()) "None yet" else "${s.trackedApps.size} selected" + if (!gate.pro && s.trackedApps.size > 1) ", free watches 1" else "")
                        }
                        Text("Choose", color = Ember.Glow, style = MaterialTheme.typography.labelLarge)
                    }
                    Rule()
                    Toggle("Live detection", "Recognizes returns within seconds using a small persistent notification. Without it, CueBack checks every 15 minutes.", s.liveDetection) { vm.setLiveDetection(ctx, it) }
                    Rule()
                    Toggle("Pause collection", "Stops observing right away. Manual saves still work.", s.collectionPaused) { vm.setCollectionPaused(ctx, it) }
                }
            }

            item { SectionTitle("Warm start") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DepthPreference.entries.forEach { p ->
                        EmberChip(
                            when (p) { DepthPreference.AUTO -> "Adapt to time away"; DepthPreference.CONCISE -> "Concise"; DepthPreference.FULL -> "Always full" },
                            selected = s.depthPreference == p,
                            onClick = { vm.setDepth(p) },
                            single = true,
                        )
                    }
                }
            }

            item { SectionTitle("Notifications") }
            item {
                Group {
                    Toggle("Notifications", "Resume suggestions and \"what's next?\" prompts.", s.notificationsEnabled, vm::setNotifications)
                    Rule()
                    Toggle("Hide details on lock screen", "Lock screen shows only \"Your next action is ready\".", s.lockScreenPrivate) { v -> vm.update { it.copy(lockScreenPrivate = v) } }
                    Rule()
                    Toggle("Quiet hours (${s.quietStartHour}:00–${s.quietEndHour}:00)", "No notifications during these hours.", s.quietHoursEnabled) { v -> vm.update { it.copy(quietHoursEnabled = v) } }
                    Rule()
                    Toggle(
                        "Unresolved context reminders" + if (!gate.pro) " (Pro)" else "",
                        "At most one reminder per paused context, after a day. Ignored reminders are never repeated.",
                        s.remindersEnabled && gate.pro,
                    ) { v -> if (!gate.pro) onPaywall() else vm.update { it.copy(remindersEnabled = v) } }
                }
            }
            if (!vm.pushConfigured) item { Hint("Push campaigns are not configured in this build. Local notifications still work.") }

            item { SectionTitle("Privacy") }
            item {
                Group {
                    Toggle("Name the apps that pulled me away", "Shown only on this device, in \"While you were away\". Off: CueBack shows a count instead.", s.showInterruptionAppNames) { v -> vm.update { it.copy(showInterruptionAppNames = v) } }
                    Rule()
                    AiSection(vm, s.cloudAiEnabled, s.aiBaseUrl, s.aiModel)
                }
            }
            item { GlassButton("Export my data (JSON)", { exporter.launch("cueback-export.json") }, Modifier.fillMaxWidth()) }
            item { GlassButton("Delete all CueBack data", { confirmDelete = true }, Modifier.fillMaxWidth(), contentColor = MaterialTheme.colorScheme.error) }
            item { TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }) { Text("Privacy policy") } }

            item { SectionTitle("Subscription") }
            item {
                Group {
                    Text(
                        when (val e = ent) {
                            EntitlementState.NotConfigured -> "Billing isn't configured in this build."
                            EntitlementState.Loading -> "Checking your plan…"
                            EntitlementState.Free -> "Free plan."
                            is EntitlementState.Pro -> if (e.isTrial) "CueBack Pro, free trial." else "CueBack Pro."
                            is EntitlementState.Error -> "Couldn't reach the billing service: ${e.message}. Free features keep working."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ember.Cream,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    if (vm.billingConfigured) {
                        FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!gate.pro) EmberButton("See Pro", onPaywall)
                            GlassButton("Restore purchases", vm::restorePurchases)
                            if (gate.pro) GlassButton("Manage in Google Play", {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${ctx.packageName}")))
                            })
                        }
                    }
                }
            }
            if (BuildConfig.DEMO_FIXTURES) {
                item { SectionTitle("Developer") }
                item {
                    Group {
                        Hint("Replays a recorded JWT refresh-bug session through the real engine. Demo contexts are labelled and can be deleted.", Modifier.padding(top = 8.dp))
                        GlassButton("Replay demo story", { vm.replayDemo(onDemo) }, Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            item { Hint("CueBack ${BuildConfig.VERSION_NAME}", Modifier.padding(vertical = 24.dp)) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all CueBack data?") },
            text = { Text("This removes every context, note, re-entry measurement, setting and saved key from this device and stops all collection. It can't be undone. Your subscription is not affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAll(onDeleted) }) { Text("Delete everything") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ember.Cream)
            subtitle?.let { Hint(it) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AiSection(vm: SettingsViewModel, enabled: Boolean, url: String, model: String) {
    var on by remember(enabled) { mutableStateOf(enabled) }
    var u by remember(url) { mutableStateOf(url) }
    var m by remember(model) { mutableStateOf(model) }
    var k by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Toggle("Cloud AI refinement", "Off by default. When on, you can ask an OpenAI-compatible provider you choose to polish a context. Secrets are redacted and file contents are never sent.", on) { on = it }
        if (on) {
            GlassTextField(u, { u = it.take(300) }, label = "Endpoint (https://…/v1)", singleLine = true, modifier = Modifier.fillMaxWidth())
            GlassTextField(m, { m = it.take(100) }, label = "Model", singleLine = true, modifier = Modifier.fillMaxWidth())
            GlassTextField(k, { k = it.take(300) }, label = if (vm.hasAiKey) "API key (saved, enter to replace)" else "API key", singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Hint("The key is encrypted with this device's keystore and never leaves it except to your endpoint.")
        }
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Save AI settings", { vm.saveAi(on, u, m, k); k = "" })
            if (vm.hasAiKey) GlassButton("Remove key", vm::clearAiKey)
        }
    }
}

@Composable
fun AppPickerScreen(onBack: () -> Unit, onPaywall: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val apps by vm.apps.collectAsState()
    val s by vm.settings.collectAsState()
    val ent by vm.entitlement.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadApps() }
    val gate = FeatureGate(ent)
    EmberScaffold(title = "Apps to watch", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                MascotSays(
                    "Pick the apps where your real work happens: browser, docs, terminal, notes, reader. I only notice when these come and go, never what's on screen.",
                    pose = MascotPose.Turn,
                )
                if (!gate.pro) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hint("Free watches 1 app (the first alphabetically by package). Pro watches all of them.", Modifier.weight(1f))
                    EmberButton("See Pro", onPaywall, compact = true)
                }
                GlassTextField(query, { query = it.take(50) }, label = "Search apps", singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            }
            val active = gate.effectiveTracked(s.trackedApps)
            val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .sortedByDescending { it.packageName in s.trackedApps }
            items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in s.trackedApps
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { vm.toggleApp(app.packageName, !checked) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(app.packageName, size = 34)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Ember.Cream)
                        if (checked && app.packageName !in active) Hint("Selected, active with Pro")
                    }
                    Checkbox(checked = checked, onCheckedChange = { vm.toggleApp(app.packageName, it) })
                }
            }
        }
    }
}
