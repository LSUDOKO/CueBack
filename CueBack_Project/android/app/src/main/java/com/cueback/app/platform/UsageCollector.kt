package com.cueback.app.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.WorkEvent

/** Placeholder package for any app the user has not chosen to track; its identity is never stored. */
const val OTHER_APP = "~other"

data class RawUsage(val timestamp: Long, val kind: Kind, val packageName: String?) {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF, KEYGUARD_SHOWN, KEYGUARD_HIDDEN }
}

/**
 * Converts raw platform usage events into structured work events. Pure so it can be unit tested
 * without an Android framework.
 */
object UsageMapper {
    fun toWorkEvents(raw: List<RawUsage>, ownPackage: String, isTracked: (String) -> Boolean): List<WorkEvent> {
        val out = mutableListOf<WorkEvent>()
        var locked = false
        for (r in raw.sortedBy { it.timestamp }) when (r.kind) {
            RawUsage.Kind.RESUMED -> {
                val pkg = r.packageName ?: continue
                if (pkg == ownPackage) continue
                val mapped = if (isTracked(pkg)) pkg else OTHER_APP
                if (out.lastOrNull()?.let { it.type == EventType.APP_FOCUSED && it.packageName == mapped } == true) continue
                out += WorkEvent(r.timestamp, EventType.APP_FOCUSED, packageName = mapped)
                locked = false
            }
            RawUsage.Kind.SCREEN_OFF, RawUsage.Kind.KEYGUARD_SHOWN -> if (!locked) {
                out += WorkEvent(r.timestamp, EventType.DEVICE_LOCKED)
                locked = true
            }
            RawUsage.Kind.KEYGUARD_HIDDEN -> if (locked) {
                out += WorkEvent(r.timestamp, EventType.DEVICE_UNLOCKED)
                locked = false
            }
            RawUsage.Kind.PAUSED -> Unit
        }
        return out
    }

    /** Foreground time per package between two instants, from RESUMED/PAUSED pairs. */
    fun foreground(raw: List<RawUsage>, from: Long, to: Long, ownPackage: String): List<AppUsage> {
        val totals = HashMap<String, Long>()
        var current: String? = null
        var since = from
        for (r in raw.sortedBy { it.timestamp }) {
            val t = r.timestamp.coerceIn(from, to)
            when (r.kind) {
                RawUsage.Kind.RESUMED -> {
                    current?.let { totals[it] = (totals[it] ?: 0) + (t - since) }
                    current = r.packageName?.takeIf { it != ownPackage }
                    since = t
                }
                RawUsage.Kind.PAUSED -> if (r.packageName == current) {
                    current?.let { totals[it] = (totals[it] ?: 0) + (t - since) }
                    current = null
                }
                RawUsage.Kind.SCREEN_OFF, RawUsage.Kind.KEYGUARD_SHOWN -> {
                    current?.let { totals[it] = (totals[it] ?: 0) + (t - since) }
                    current = null
                }
                RawUsage.Kind.KEYGUARD_HIDDEN -> Unit
            }
        }
        current?.let { totals[it] = (totals[it] ?: 0) + (to - since) }
        return totals.filter { it.value > 0 }.map { AppUsage(it.key, it.value) }.sortedByDescending { it.foregroundMs }
    }
}

class UsageCollector(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun raw(from: Long, to: Long): List<RawUsage> {
        if (!hasPermission() || to <= from) return emptyList()
        val events = usm.queryEvents(from, to) ?: return emptyList()
        val out = mutableListOf<RawUsage>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> RawUsage.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> RawUsage.Kind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> RawUsage.Kind.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_SHOWN -> RawUsage.Kind.KEYGUARD_SHOWN
                UsageEvents.Event.KEYGUARD_HIDDEN -> RawUsage.Kind.KEYGUARD_HIDDEN
                else -> null
            } ?: continue
            out += RawUsage(e.timeStamp, kind, e.packageName)
        }
        return out
    }

    fun events(from: Long, to: Long, isTracked: (String) -> Boolean): List<WorkEvent> =
        UsageMapper.toWorkEvents(raw(from, to), context.packageName, isTracked)

    fun usageBetween(from: Long, to: Long): List<AppUsage> =
        UsageMapper.foreground(raw(from, to), from, to, context.packageName)
}
