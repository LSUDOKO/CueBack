package com.cueback.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val packageName: String, val label: String)

interface AppCatalogApi {
    fun labelOf(pkg: String): String
    fun versionOf(pkg: String): Long?
}

/** Launchable apps and their labels/versions. Only used locally; never sent anywhere. */
class AppCatalog(private val context: Context) : AppCatalogApi {
    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()

    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, labelOf(it)) }
            .sortedBy { it.label.lowercase() }
    }

    override fun labelOf(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            .getOrElse { pkg.substringAfterLast('.').replaceFirstChar(Char::uppercase) }
    }

    fun iconOf(pkg: String): Drawable? = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()

    /** null when the app is not installed. */
    override fun versionOf(pkg: String): Long? = runCatching { pm.getPackageInfo(pkg, 0).longVersionCode }.getOrNull()

    fun launchIntent(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)
}
