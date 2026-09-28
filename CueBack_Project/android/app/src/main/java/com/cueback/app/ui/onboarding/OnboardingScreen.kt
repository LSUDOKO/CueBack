package com.cueback.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cueback.app.core.model.UseCase
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.RibbonNext
import com.cueback.app.ui.components.VSpace
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.settings.SettingsViewModel
import com.cueback.app.ui.settings.Toggle
import com.cueback.app.core.model.Fact
import kotlinx.coroutines.launch

private const val PAGES = 4

@Composable
fun OnboardingScreen(onChooseApps: () -> Unit, onDone: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val usageOk by vm.usageGranted.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) { vm.refreshPermissions(); onPauseOrDispose { } }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            LinearProgressIndicator(progress = { (page + 1f) / PAGES }, modifier = Modifier.fillMaxWidth())
            VSpace(28)
            AnimatedContent(page, modifier = Modifier.weight(1f), label = "onboarding") { p ->
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (p) {
                        0 -> {
                            Text("Don't save the task.\nSave your place.", style = MaterialTheme.typography.displaySmall)
                            Hint("Interruptions rarely make you forget the goal. They make you forget where you were: what worked, what failed, and what you meant to do next.")
                            Hint("CueBack notices when you step away from work, keeps your place, and hands it back when you return.")
                            RibbonNext(Fact.user("Run the expired-token test in auth/refresh_test.go"), large = false)
                        }
                        1 -> {
                            Text("What CueBack sees", style = MaterialTheme.typography.headlineMedium)
                            Hint("Only the apps you pick, and only which one is in front and when the screen locks. That's how it knows you paused and when you're back.")
                            Text("What it never records", style = MaterialTheme.typography.titleMedium)
                            Hint("Your screen, audio, keystrokes, messages, or the content of other apps. Links and files are saved only when you share them to CueBack.")
                            Text("Where it lives", style = MaterialTheme.typography.titleMedium)
                            Hint("On this phone. Cloud AI is off until you turn it on. You can pause collection, export everything, or delete everything from Settings at any time.")
                        }
                        2 -> {
                            Text("What do you mostly do?", style = MaterialTheme.typography.headlineMedium)
                            Hint("This tunes wording and filters. You can change it later.")
                            UseCase.entries.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { u ->
                                        FilterChip(
                                            selected = s.useCase == u,
                                            onClick = { vm.update { it.copy(useCase = u) } },
                                            label = { Text(u.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Text("Turn on automatic detection", style = MaterialTheme.typography.headlineMedium)
                            Hint("Each step is optional. Skip them and you can still save your place by hand.")
                            Step("1. Allow usage access", if (usageOk) "Allowed" else "Android asks you to switch CueBack on in a list.", usageOk) {
                                ctx.startActivity(vm.usageAccessIntent(ctx))
                            }
                            Step("2. Choose apps to watch", if (s.trackedApps.isEmpty()) "For example your browser, docs, terminal or notes app." else "${s.trackedApps.size} selected", s.trackedApps.isNotEmpty(), onChooseApps)
                            if (Build.VERSION.SDK_INT >= 33) {
                                Step("3. Allow notifications", "So CueBack can say \"you're back\" and ask what's next.", false) {
                                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            Toggle("Live detection", "Recognize returns within seconds. Shows a small persistent notification.", s.liveDetection) { v -> vm.update { it.copy(liveDetection = v) } }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) TextButton(onClick = { page-- }) { Text("Back") }
                Row(Modifier.weight(1f)) {}
                Button(onClick = {
                    if (page < PAGES - 1) page++ else scope.launch { vm.finishOnboarding(ctx).join(); onDone() }
                }) { Text(if (page < PAGES - 1) "Continue" else "Start using CueBack") }
            }
        }
    }
}

@Composable
private fun Step(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Hint(subtitle)
        }
        if (done) Text("Done", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        else OutlinedButton(onClick = onClick) { Text("Open") }
    }
}
