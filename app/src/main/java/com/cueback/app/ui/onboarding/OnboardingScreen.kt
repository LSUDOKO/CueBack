package com.cueback.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cueback.app.core.model.Fact
import com.cueback.app.core.model.UseCase
import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.EmberChip
import com.cueback.app.ui.components.GlassButton
import com.cueback.app.ui.components.GlassCard
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.Mascot
import com.cueback.app.ui.components.MascotPose
import com.cueback.app.ui.components.MascotSays
import com.cueback.app.ui.components.RibbonNext
import com.cueback.app.ui.components.VSpace
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.settings.SettingsViewModel
import com.cueback.app.ui.settings.Toggle
import com.cueback.app.ui.theme.Backdrop
import com.cueback.app.ui.theme.Ember
import com.cueback.app.ui.theme.EmberBackdrop
import kotlinx.coroutines.launch

private const val PAGES = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onChooseApps: () -> Unit, onDone: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val usageOk by vm.usageGranted.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifOk by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        notifOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    EmberBackdrop(Backdrop.Night) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
            StepBars(page)
            VSpace(18)
            AnimatedContent(
                page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(240, delayMillis = 80)) + slideInHorizontally(tween(300)) { dir * it / 8 }) togetherWith fadeOut(tween(80))
                },
                label = "onboarding",
            ) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (p) {
                        0 -> {
                            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                                Mascot(MascotPose.Hero, size = 210.dp, contentDescription = "Cue, the CueBack owl")
                            }
                            Text(
                                "Don't save the task.\nSave your place.",
                                style = MaterialTheme.typography.displaySmall,
                                color = Ember.Cream,
                                modifier = Modifier.semantics { heading() },
                            )
                            Hint("Interruptions rarely make you forget the goal. They make you forget where you were: what worked, what failed, and what you meant to do next.")
                            Hint("CueBack notices when you step away from work, keeps your place, and hands it back when you return.")
                            RibbonNext(Fact.user("Run the expired-token test in auth/refresh_test.go"), large = false)
                        }
                        1 -> {
                            MascotSays("I only look at which of your chosen apps is open. Never at what's on the screen.", size = 84.dp)
                            Text("What CueBack sees", style = MaterialTheme.typography.headlineMedium, color = Ember.Cream, modifier = Modifier.semantics { heading() })
                            Hint("Only the apps you pick, and only which one is in front and when the screen locks. That's how it knows you paused and when you're back.")
                            GlassCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("What it never records", style = MaterialTheme.typography.titleMedium, color = Ember.Cream)
                                Hint("Your screen, audio, keystrokes, messages, or the content of other apps. Links and files are saved only when you share them to CueBack.")
                            }
                            GlassCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Where it lives", style = MaterialTheme.typography.titleMedium, color = Ember.Cream)
                                Hint("On this phone. Cloud AI is off until you turn it on. You can pause collection, export everything, or delete everything from Settings at any time.")
                            }
                        }
                        2 -> {
                            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                                Mascot(MascotPose.Think, size = 170.dp)
                            }
                            Text("What do you mostly do?", style = MaterialTheme.typography.headlineMedium, color = Ember.Cream, modifier = Modifier.semantics { heading() })
                            Hint("This tunes wording and filters. You can change it later.")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                UseCase.entries.forEach { u ->
                                    EmberChip(
                                        u.name.lowercase().replaceFirstChar(Char::uppercase),
                                        selected = s.useCase == u,
                                        onClick = { vm.update { it.copy(useCase = u) } },
                                        single = true,
                                    )
                                }
                            }
                        }
                        else -> {
                            MascotSays("Three switches, and I do the remembering for you.", pose = MascotPose.Turn, size = 84.dp)
                            Text("Turn on automatic detection", style = MaterialTheme.typography.headlineMedium, color = Ember.Cream, modifier = Modifier.semantics { heading() })
                            Hint("Each step is optional. Skip them and you can still save your place by hand.")
                            GlassCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Step("1. Allow usage access", if (usageOk) "Allowed" else "Android asks you to switch CueBack on in a list.", usageOk) {
                                    ctx.startActivity(vm.usageAccessIntent(ctx))
                                }
                                Rule()
                                Step("2. Choose apps to watch", if (s.trackedApps.isEmpty()) "For example your browser, docs, terminal or notes app." else "${s.trackedApps.size} selected", s.trackedApps.isNotEmpty(), onChooseApps)
                                if (Build.VERSION.SDK_INT >= 33) {
                                    Rule()
                                    Step("3. Allow notifications", if (notifOk) "Allowed" else "So CueBack can say \"you're back\" and ask what's next.", notifOk) {
                                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                Rule()
                                Toggle("Live detection", "Recognize returns within seconds. Shows a small persistent notification.", s.liveDetection) { v -> vm.update { it.copy(liveDetection = v) } }
                            }
                        }
                    }
                    VSpace(8)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) GlassButton("Back", { page-- })
                Spacer(Modifier.weight(1f))
                EmberButton(
                    if (page < PAGES - 1) "Continue" else "Start using CueBack",
                    onClick = { if (page < PAGES - 1) page++ else scope.launch { vm.finishOnboarding(ctx).join(); onDone() } },
                )
            }
        }
    }
}

/** Four bars, one per page. They show where you are and how much is left. */
@Composable
private fun StepBars(page: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGES) { i ->
            val fill by animateFloatAsState(if (i <= page) 1f else 0f, tween(320), label = "bar")
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color(0x33FFF3E8))) {
                Box(Modifier.fillMaxWidth(fill).height(4.dp).clip(CircleShape).background(Ember.Cream))
            }
        }
    }
}

@Composable
private fun Rule() = HorizontalDivider(color = Color(0x1FFFF3E8))

@Composable
private fun Step(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ember.Cream)
            Hint(subtitle)
        }
        if (done) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Ember.Glow, modifier = Modifier.size(18.dp))
                Text("Done", color = Ember.Glow, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            GlassButton("Open", onClick)
        }
    }
}
