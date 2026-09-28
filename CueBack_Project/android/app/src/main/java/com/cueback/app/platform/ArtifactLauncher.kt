package com.cueback.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import java.net.URI

sealed interface LaunchTarget {
    data class Web(val url: String) : LaunchTarget
    data class Content(val uri: String) : LaunchTarget
    data class App(val packageName: String) : LaunchTarget
}

/**
 * Only safe, expected targets can be launched: http(s) links, content:// documents the user shared,
 * and installed apps by package name. javascript:, file://, intent: and anything else is rejected.
 */
object ArtifactPolicy {
    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val URL_IN_TEXT = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun target(a: Artifact): LaunchTarget? = when (a.type) {
        ArtifactType.URL -> safeWebUrl(a.locator)?.let(LaunchTarget::Web)
        ArtifactType.FILE -> a.locator.takeIf { it.startsWith("content://") && !it.contains("..") }?.let(LaunchTarget::Content)
        ArtifactType.APP -> a.locator.takeIf { PACKAGE.matches(it) }?.let(LaunchTarget::App)
        ArtifactType.TEXT -> null
    }

    fun safeWebUrl(raw: String): String? {
        val s = raw.trim()
        if (s.length > 2048) return null
        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return s
    }

    /** Extracts the first web link from shared text (browsers often share "Title https://…"). */
    fun firstUrl(text: String): String? = URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ']')?.let(::safeWebUrl)
}

sealed interface LaunchResult {
    data object Opened : LaunchResult
    data class Unavailable(val reason: String) : LaunchResult
}

class ArtifactLauncher(private val context: Context, private val catalog: AppCatalog) {
    fun launch(a: Artifact): LaunchResult {
        val target = ArtifactPolicy.target(a) ?: return LaunchResult.Unavailable("This item can't be opened safely, so CueBack shows it instead.")
        val intent = when (target) {
            is LaunchTarget.Web -> Intent(Intent.ACTION_VIEW, Uri.parse(target.url))
            is LaunchTarget.Content -> Intent(Intent.ACTION_VIEW, Uri.parse(target.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            is LaunchTarget.App -> catalog.launchIntent(target.packageName)
                ?: return LaunchResult.Unavailable("${catalog.labelOf(target.packageName)} is not installed.")
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            LaunchResult.Opened
        } catch (_: ActivityNotFoundException) {
            LaunchResult.Unavailable("No app can open this item.")
        } catch (_: SecurityException) {
            LaunchResult.Unavailable("Access to this file has expired. Share it to CueBack again to reattach it.")
        }
    }
}
