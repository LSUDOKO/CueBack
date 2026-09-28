package com.cueback.app.notify

import java.net.URI

sealed interface DeepLink {
    data class WarmStart(val contextId: String, val source: String) : DeepLink
    data class ContextDetail(val contextId: String) : DeepLink
    data object Library : DeepLink
    data object Capture : DeepLink
    data object Home : DeepLink
}

/**
 * Parses only CueBack's own scheme. Anything else — other schemes, hosts, malformed ids — is rejected,
 * so a notification payload can never navigate the app somewhere unexpected.
 */
object DeepLinks {
    const val SCHEME = "cueback"
    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val SOURCES = setOf("notification", "push", "home", "library", "auto", "demo", "reminder")

    fun warmStart(id: String, source: String = "notification") = "$SCHEME://warmstart/$id?src=$source"
    fun context(id: String) = "$SCHEME://context/$id"

    fun parse(raw: String?): DeepLink? {
        if (raw.isNullOrBlank() || raw.length > 256) return null
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val src = uri.query.orEmpty().split('&').mapNotNull {
            val (k, v) = it.split('=', limit = 2).let { p -> p.getOrNull(0) to p.getOrNull(1) }
            if (k == "src") v else null
        }.firstOrNull()?.takeIf { it in SOURCES } ?: "notification"
        return when (uri.host?.lowercase()) {
            "warmstart" -> segments.singleOrNull()?.takeIf { ID.matches(it) }?.let { DeepLink.WarmStart(it, src) }
            "context" -> segments.singleOrNull()?.takeIf { ID.matches(it) }?.let { DeepLink.ContextDetail(it) }
            "library" -> DeepLink.Library.takeIf { segments.isEmpty() }
            "capture" -> DeepLink.Capture.takeIf { segments.isEmpty() }
            "home" -> DeepLink.Home.takeIf { segments.isEmpty() }
            else -> null
        }
    }

    /** OneSignal payloads carry `context_id` in additional data; fall back to the launch URL. */
    fun fromPush(contextId: String?, launchUrl: String?): DeepLink? =
        contextId?.takeIf { ID.matches(it) }?.let { DeepLink.WarmStart(it, "push") } ?: parse(launchUrl)
}
