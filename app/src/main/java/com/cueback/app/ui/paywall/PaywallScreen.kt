package com.cueback.app.ui.paywall

import com.cueback.app.ui.components.EmberButton
import com.cueback.app.ui.components.GlassButton
import com.cueback.app.ui.components.GlassIconButton
import com.cueback.app.ui.components.Mascot
import com.cueback.app.ui.components.MascotPose
import com.cueback.app.ui.components.glass
import com.cueback.app.ui.theme.Backdrop
import com.cueback.app.ui.theme.Ember
import com.cueback.app.ui.theme.EmberBackdrop
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.revenuecat.purchases.PackageType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.billing.EntitlementState
import com.cueback.app.billing.PlanOption
import com.cueback.app.billing.PurchaseOutcome
import com.cueback.app.data.repo.Metric
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.VSpace
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.theme.Ribbon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUi(
    val loading: Boolean = true,
    val plans: List<PlanOption> = emptyList(),
    val selected: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class PaywallViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(PaywallUi())
    val ui = _ui.asStateFlow()
    val entitlement = c.billing.state

    init {
        viewModelScope.launch {
            c.analytics.track(Metric.PAYWALL_VIEWED)
            c.billing.plans()
                .onSuccess { p -> _ui.value = PaywallUi(loading = false, plans = p, selected = p.firstOrNull()?.id) }
                .onFailure { e -> _ui.value = PaywallUi(loading = false, error = e.message) }
        }
    }

    fun select(id: String) = _ui.update { it.copy(selected = id) }

    fun purchase(activity: Activity) {
        val plan = _ui.value.plans.firstOrNull { it.id == _ui.value.selected } ?: return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val outcome = c.billing.purchase(activity, plan)
            when (outcome) {
                PurchaseOutcome.Success -> {
                    val trial = (c.billing.state.value as? EntitlementState.Pro)?.isTrial == true
                    c.analytics.track(if (trial) Metric.TRIAL_STARTED else Metric.PURCHASE_COMPLETED)
                }
                is PurchaseOutcome.Failed -> c.analytics.track(Metric.PURCHASE_FAILED)
                else -> Unit
            }
            _ui.update {
                it.copy(
                    busy = false,
                    message = when (outcome) {
                        PurchaseOutcome.Success -> null
                        PurchaseOutcome.Cancelled -> "Purchase cancelled. Nothing was charged."
                        PurchaseOutcome.Pending -> "Your payment is pending. Pro unlocks as soon as Google Play confirms it."
                        is PurchaseOutcome.Failed -> outcome.message
                    },
                )
            }
        }
    }

    fun restore() {
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val outcome = c.billing.restore()
            if (outcome == PurchaseOutcome.Success) c.analytics.track(Metric.RESTORE_COMPLETED)
            _ui.update { it.copy(busy = false, message = (outcome as? PurchaseOutcome.Failed)?.message) }
        }
    }
}

private val BENEFITS = listOf(
    "Unlimited open contexts" to "Free keeps three. Pro keeps every place you've paused.",
    "Watch all your work apps" to "Automatic pause and return detection across every app you choose, not just one.",
    "Full reconstruction" to "Goal, everything done, artifacts, evidence and what changed — for long breaks.",
    "Voice capture" to "Say the next step instead of typing it.",
    "Context timeline and reminders" to "See how a task evolved, and get one useful nudge for work left waiting.",
)

@Composable
fun PaywallScreen(onClose: () -> Unit) {
    val vm = containerViewModel { PaywallViewModel(it) }
    val ui by vm.ui.collectAsState()
    val ent by vm.entitlement.collectAsState()
    val activity = LocalActivity.current

    EmberBackdrop(Backdrop.Night) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                GlassIconButton(Icons.Default.Close, "Close", onClose)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ent is EntitlementState.Pro) {
                    val pro = ent as EntitlementState.Pro
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Mascot(MascotPose.Wave, size = 170.dp) }
                    Text("You have CueBack Pro.", style = MaterialTheme.typography.displaySmall, color = Ember.Cream)
                    Hint(if (pro.isTrial) "You're in your free trial. Manage or cancel any time in Google Play." else "Thanks for supporting CueBack. Manage your subscription in Google Play.")
                    VSpace(8)
                    EmberButton("Done", onClose)
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Keep that continuity across every project.",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ember.Cream,
                        modifier = Modifier.weight(1f),
                    )
                    Mascot(MascotPose.Hero, size = 112.dp)
                }
                Hint("Pick up any paused project exactly where you stopped, not just one.")
                // Plans sit above the benefits so the choice is visible without scrolling.
                when {
                    ui.loading -> CircularProgressIndicator(color = Ember.Glow)
                    ui.error != null -> Hint("Plans can't load right now: ${ui.error}")
                    else -> ui.plans.forEach { p -> PlanRow(p, p.id == ui.selected) { vm.select(p.id) } }
                }
                VSpace(4)
                BENEFITS.forEach { (t, d) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(8.dp).background(Ribbon, CircleShape))
                        Column {
                            Text(t, style = MaterialTheme.typography.titleMedium, color = Ember.Cream)
                            Hint(d)
                        }
                    }
                }
                VSpace(8)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val plan = ui.plans.firstOrNull { it.id == ui.selected }
                // Outside the scroll area, so a purchase result is always on screen next to the button.
                ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                EmberButton(
                    if (plan?.trial != null) "Start free trial" else "Continue with Pro",
                    onClick = { activity?.let(vm::purchase) },
                    enabled = plan != null && !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassButton("Continue free", onClose, Modifier.weight(1f))
                    GlassButton("Restore purchases", vm::restore, Modifier.weight(1f), enabled = !ui.busy && ui.error == null)
                }
                plan?.let { Text(priceLine(it), style = MaterialTheme.typography.bodySmall, color = Ember.Ash, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

private fun priceLine(p: PlanOption): String = when (p.type) {
    PackageType.LIFETIME -> "${p.price} once. Yours to keep, no subscription."
    PackageType.ANNUAL -> "${p.trial?.let { "$it, then " } ?: ""}${p.price} per year. Cancel any time in Google Play."
    else -> "${p.trial?.let { "$it, then " } ?: ""}${p.price} per month. Cancel any time in Google Play."
}

@Composable
private fun PlanRow(p: PlanOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.medium, if (selected) Ember.GlassStrong else Ember.Glass)
            .then(if (selected) Modifier.border(1.5.dp, Ember.Flame, MaterialTheme.shapes.medium) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = Ember.Flame, unselectedColor = Ember.Ash))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium, color = Ember.Cream)
            listOfNotNull(p.trial, p.perMonth).takeIf { it.isNotEmpty() }?.let { Hint(it.joinToString(", ")) }
        }
        Text(p.price, style = MaterialTheme.typography.titleMedium, color = Ember.Cream, modifier = Modifier.padding(end = 10.dp))
    }
}
