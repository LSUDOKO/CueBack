package com.cueback.app.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

const val PRIVACY_POLICY_URL = "https://github.com/LSUDOKO/CueBack/blob/main/CueBack_Project/PRIVACY_POLICY.md"

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { SectionTitle("Detection") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Usage access", style = MaterialTheme.typography.bodyLarge)
                        Hint(if (usageOk) "Granted. CueBack reads which app is in front and when the screen locks." else "Needed to notice pauses and returns automatically.")
                    }
                    TextButton(onClick = { ctx.startActivity(vm.usageAccessIntent(ctx)) }) { Text(if (usageOk) "Manage" else "Allow") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onApps).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Apps to watch", style = MaterialTheme.typography.bodyLarge)
                        Hint(if (s.trackedApps.isEmpty()) "None yet" else "${s.trackedApps.size} selected" + if (!gate.pro && s.trackedApps.size > 1) ", free watches 1" else "")
                    }
                    Text("Choose", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            item { Toggle("Live detection", "Recognizes returns within seconds using a small persistent notification. Without it, CueBack checks every 15 minutes.", s.liveDetection) { vm.setLiveDetection(ctx, it) } }
            item { Toggle("Pause collection", "Stops observing right away. Manual saves still work.", s.collectionPaused) { vm.setCollectionPaused(ctx, it) } }

            item { SectionTitle("Warm start") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DepthPreference.entries.forEach { p ->
                        FilterChip(selected = s.depthPreference == p, onClick = { vm.setDepth(p) }, label = {
                            Text(when (p) { DepthPreference.AUTO -> "Adapt to time away"; DepthPreference.CONCISE -> "Concise"; DepthPreference.FULL -> "Always full" })
                        })
                    }
                }
            }

            item { SectionTitle("Notifications") }
            item { Toggle("Notifications", "Resume suggestions and \"what's next?\" prompts.", s.notificationsEnabled, vm::setNotifications) }
            item { Toggle("Hide details on lock screen", "Lock screen shows only \"Your next action is ready\".", s.lockScreenPrivate) { v -> vm.update { it.copy(lockScreenPrivate = v) } } }
            item { Toggle("Quiet hours (${s.quietStartHour}:00–${s.quietEndHour}:00)", "No notifications during these hours.", s.quietHoursEnabled) { v -> vm.update { it.copy(quietHoursEnabled = v) } } }
            item {
                Toggle(
                    "Unresolved context reminders" + if (!gate.pro) " (Pro)" else "",
                    "At most one reminder per paused context, after a day. Ignored reminders are never repeated.",
                    s.remindersEnabled && gate.pro,
                ) { v -> if (!gate.pro) onPaywall() else vm.update { it.copy(remindersEnabled = v) } }
            }
            if (!vm.pushConfigured) item { Hint("Push campaigns are not configured in this build. Local notifications still work.") }

            item { SectionTitle("Privacy") }
            item { Toggle("Name the apps that pulled me away", "Shown only on this device, in \"While you were away\". Off: CueBack shows a count instead.", s.showInterruptionAppNames) { v -> vm.update { it.copy(showInterruptionAppNames = v) } } }
            item { AiSection(vm, s.cloudAiEnabled, s.aiBaseUrl, s.aiModel) }
            item { OutlinedButton(onClick = { exporter.launch("cueback-export.json") }, modifier = Modifier.fillMaxWidth()) { Text("Export my data (JSON)") } }
            item { OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all CueBack data", color = MaterialTheme.colorScheme.error) } }
            item { TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }) { Text("Privacy policy") } }

            item { SectionTitle("Subscription") }
            item {
                Hint(
                    when (val e = ent) {
                        EntitlementState.NotConfigured -> "Billing isn't configured in this build."
                        EntitlementState.Loading -> "Checking your plan…"
                        EntitlementState.Free -> "Free plan."
                        is EntitlementState.Pro -> if (e.isTrial) "CueBack Pro, free trial." else "CueBack Pro."
                        is EntitlementState.Error -> "Couldn't reach the billing service: ${e.message}. Free features keep working."
                    },
                )
            }
            if (vm.billingConfigured) item {
                Row {
                    if (!gate.pro) TextButton(onClick = onPaywall) { Text("See Pro") }
                    TextButton(onClick = vm::restorePurchases) { Text("Restore purchases") }
                    if (gate.pro) TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${ctx.packageName}")))
                    }) { Text("Manage in Google Play") }
                }
            }
            if (BuildConfig.DEMO_FIXTURES) {
                item { SectionTitle("Developer") }
                item {
                    Hint("Replays a recorded JWT refresh-bug session through the real engine. Demo contexts are labelled and can be deleted.")
                    TextButton(onClick = { vm.replayDemo(onDemo) }) { Text("Replay demo story") }
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Toggle("Cloud AI refinement", "Off by default. When on, you can ask an OpenAI-compatible provider you choose to polish a context. Secrets are redacted and file contents are never sent.", on) { on = it }
        if (on) {
            OutlinedTextField(u, { u = it.take(300) }, label = { Text("Endpoint (https://…/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(m, { m = it.take(100) }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(k, { k = it.take(300) }, label = { Text(if (vm.hasAiKey) "API key (saved, enter to replace)" else "API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Hint("The key is encrypted with this device's keystore and never leaves it except to your endpoint.")
        }
        Row {
            TextButton(onClick = { vm.saveAi(on, u, m, k); k = "" }) { Text("Save AI settings") }
            if (vm.hasAiKey) TextButton(onClick = vm::clearAiKey) { Text("Remove key") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(onBack: () -> Unit, onPaywall: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val apps by vm.apps.collectAsState()
    val s by vm.settings.collectAsState()
    val ent by vm.entitlement.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadApps() }
    val gate = FeatureGate(ent)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Apps to watch") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                Hint("Pick the apps where your real work happens: browser, docs, terminal, notes, reader. CueBack only notices when these come and go — never what's on screen.")
                if (!gate.pro) Row(verticalAlignment = Alignment.CenterVertically) {
                    Hint("Free watches 1 app (the first alphabetically by package). Pro watches all of them.", Modifier.weight(1f))
                    TextButton(onClick = onPaywall) { Text("See Pro") }
                }
                OutlinedTextField(query, { query = it.take(50) }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
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
                    AppIcon(app.packageName, size = 32)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        if (checked && app.packageName !in active) Hint("Selected, active with Pro")
                    }
                    Checkbox(checked = checked, onCheckedChange = { vm.toggleApp(app.packageName, it) })
                }
            }
        }
    }
}
