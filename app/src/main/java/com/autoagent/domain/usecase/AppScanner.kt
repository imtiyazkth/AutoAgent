package com.autoagent.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.autoagent.domain.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scanInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launchablePackages = pm.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo.packageName }.toSet()

        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        return packages
            .filter { launchablePackages.contains(it.packageName) }
            .map { pkg ->
                val launchActivity = pm.getLaunchIntentForPackage(pkg.packageName)
                    ?.component?.className
                InstalledAppInfo(
                    packageName = pkg.packageName,
                    appName = pm.getApplicationLabel(pkg.applicationInfo).toString(),
                    versionName = pkg.versionName ?: "1.0",
                    installDate = pkg.firstInstallTime,
                    lastUpdated = pkg.lastUpdateTime,
                    canLaunch = true,
                    category = detectCategory(pkg.packageName),
                    launchActivity = launchActivity
                )
            }
            .sortedBy { it.appName }
    }

    private fun detectCategory(pkg: String): String = when {
        pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") -> "Browser"
        pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal") -> "Messaging"
        pkg.contains("gmail") || pkg.contains("mail") -> "Email"
        pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("spotify") -> "Media"
        pkg.contains("openai") || pkg.contains("anthropic") || pkg.contains("claude") -> "AI"
        pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("twitter") -> "Social"
        pkg.contains("maps") || pkg.contains("uber") || pkg.contains("ola") -> "Navigation"
        pkg.contains("camera") || pkg.contains("gallery") -> "Camera"
        pkg.contains("clock") || pkg.contains("calendar") || pkg.contains("alarm") -> "Productivity"
        pkg.contains("bank") || pkg.contains("pay") || pkg.contains("wallet") -> "Finance"
        else -> "General"
    }
}
