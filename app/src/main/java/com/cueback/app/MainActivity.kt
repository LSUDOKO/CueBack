package com.cueback.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cueback.app.data.repo.WarmStartSource
import com.cueback.app.notify.DeepLink
import com.cueback.app.notify.DeepLinks
import com.cueback.app.ui.capture.CaptureScreen
import com.cueback.app.ui.detail.ContextDetailScreen
import com.cueback.app.ui.home.HomeScreen
import com.cueback.app.ui.library.LibraryScreen
import com.cueback.app.ui.onboarding.OnboardingScreen
import com.cueback.app.ui.paywall.PaywallScreen
import com.cueback.app.ui.settings.AppPickerScreen
import com.cueback.app.ui.settings.SettingsScreen
import com.cueback.app.ui.theme.CueBackTheme
import com.cueback.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val WARM = "warm/{id}?src={src}"
    const val CONTEXT = "context/{id}"
    const val CAPTURE = "capture"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val APPS = "apps"
    const val PAYWALL = "paywall"
    fun warm(id: String, src: String = "home") = "warm/$id?src=$src"
    fun context(id: String) = "context/$id"
}

class MainActivity : ComponentActivity() {
    private val pendingLink = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-only app: keep the system bar icons light whatever the phone's theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        pendingLink.value = DeepLinks.parse(intent?.dataString)
        val container = (application as CueBackApp).container
        setContent {
            CueBackTheme {
                val onboarded by produceState<Boolean?>(null) { value = container.settings.current().onboarded }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    onboarded?.let { CueBackNav(if (it) Routes.HOME else Routes.ONBOARDING, pendingLink) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.parse(intent.dataString)?.let { pendingLink.value = it }
    }
}

@Composable
private fun CueBackNav(start: String, pendingLink: MutableStateFlow<DeepLink?>) {
    val nav = rememberNavController()
    val link by pendingLink.collectAsState()
    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        pendingLink.value = null
        if (nav.currentDestination?.route == Routes.ONBOARDING) return@LaunchedEffect
        when (l) {
            is DeepLink.WarmStart -> nav.navigate(Routes.warm(l.contextId, l.source)) { launchSingleTop = true }
            is DeepLink.ContextDetail -> nav.navigate(Routes.context(l.contextId))
            DeepLink.Library -> nav.navigate(Routes.LIBRARY)
            DeepLink.Capture -> nav.navigate(Routes.CAPTURE)
            DeepLink.Home -> nav.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    // One transition for the whole app: the new screen rises a little as it fades in.
    val reduced = LocalReducedMotion.current
    NavHost(
        nav,
        startDestination = start,
        enterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(240, delayMillis = 60)) + slideInVertically(tween(320)) { it / 24 } },
        exitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(120)) },
        popEnterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(220)) },
        popExitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(140)) + slideOutVertically(tween(220)) { it / 24 } },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onChooseApps = { nav.navigate(Routes.APPS) }, onDone = { nav.goHomeClearing() })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onWarmStart = { nav.navigate(Routes.warm(it)) },
                onContext = { nav.navigate(Routes.context(it)) },
                onCapture = { nav.navigate(Routes.CAPTURE) },
                onLibrary = { nav.navigate(Routes.LIBRARY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onFixDetection = { nav.navigate(Routes.SETTINGS) },
                onPaywall = { nav.navigate(Routes.PAYWALL) },
            )
        }
        composable(
            Routes.WARM,
            arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("src") { type = NavType.StringType; defaultValue = "home" }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val source = when (entry.arguments?.getString("src")) {
                "notification", "reminder" -> WarmStartSource.NOTIFICATION
                "push" -> WarmStartSource.NOTIFICATION
                "library" -> WarmStartSource.LIBRARY
                "demo" -> WarmStartSource.DEMO
                "auto" -> WarmStartSource.AUTO
                else -> WarmStartSource.HOME
            }
            com.cueback.app.ui.warmstart.WarmStartScreen(
                contextId = id,
                source = source,
                onClose = { nav.goHome() },
                onPaywall = { nav.navigate(Routes.PAYWALL) },
                onOpenContext = { nav.navigate(Routes.context(it)) },
                onPaywallAfterSuccess = { nav.navigate(Routes.PAYWALL) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.CONTEXT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ContextDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { if (!nav.popBackStack()) nav.goHome() },
                onWarmStart = { nav.navigate(Routes.warm(it)) },
                onPaywall = { nav.navigate(Routes.PAYWALL) },
            )
        }
        composable(Routes.CAPTURE) { CaptureScreen(onBack = { nav.popBackStack() }, onPaywall = { nav.navigate(Routes.PAYWALL) }) }
        composable(Routes.LIBRARY) { LibraryScreen(onBack = { nav.popBackStack() }, onContext = { nav.navigate(Routes.context(it)) }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onApps = { nav.navigate(Routes.APPS) },
                onPaywall = { nav.navigate(Routes.PAYWALL) },
                onDeleted = { nav.navigate(Routes.ONBOARDING) { popUpTo(0) } },
                onDemo = { nav.navigate(Routes.warm(it, "demo")) },
            )
        }
        composable(Routes.APPS) { AppPickerScreen(onBack = { nav.popBackStack() }, onPaywall = { nav.navigate(Routes.PAYWALL) }) }
        composable(Routes.PAYWALL) { PaywallScreen(onClose = { if (!nav.popBackStack()) nav.goHome() }) }
    }
}

private fun NavHostController.goHome() {
    if (!popBackStack(Routes.HOME, inclusive = false)) goHomeClearing()
}

private fun NavHostController.goHomeClearing() = navigate(Routes.HOME) { popUpTo(0) }
