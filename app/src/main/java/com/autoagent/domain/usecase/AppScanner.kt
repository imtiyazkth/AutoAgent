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
        return try {
            val pm = context.packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchable = try {
                pm.queryIntentActivities(launchIntent, 0)
                    .map { it.activityInfo.packageName }.toSet()
            } catch (e: Exception) { emptySet() }

            val packages = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
            } catch (e: Exception) { emptyList() }

            packages
                .filter { launchable.contains(it.packageName) }
                .filter { it.applicationInfo != null }   // null safety: some system entries have no applicationInfo
                .mapNotNull { pkg ->
                    try {
                        InstalledAppInfo(
                            packageName = pkg.packageName,
                            appName = try {
                                pm.getApplicationLabel(pkg.applicationInfo!!).toString()
                            } catch (e: Exception) { pkg.packageName },
                            versionName = pkg.versionName ?: "1.0",
                            installDate = pkg.firstInstallTime,
                            lastUpdated = pkg.lastUpdateTime,
                            canLaunch = launchable.contains(pkg.packageName),
                            category = detectCategory(pkg.packageName),
                            launchActivity = try {
                                pm.getLaunchIntentForPackage(pkg.packageName)
                                    ?.component?.className
                            } catch (e: Exception) { null }
                        )
                    } catch (e: Exception) { null }
                }
                .sortedBy { it.appName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun detectCategory(pkg: String): String = when {
        pkg.contains("chrome") || pkg.contains("firefox") ||
        pkg.contains("browser") || pkg.contains("opera") -> "Browser"
        pkg.contains("whatsapp") || pkg.contains("telegram") ||
        pkg.contains("signal") || pkg.contains("messenger") -> "Messaging"
        pkg.contains("gmail") || pkg.contains("mail") ||
        pkg.contains("outlook") -> "Email"
        pkg.contains("youtube") || pkg.contains("netflix") ||
        pkg.contains("spotify") || pkg.contains("music") -> "Media"
        pkg.contains("openai") || pkg.contains("anthropic") ||
        pkg.contains("claude") || pkg.contains("gemini") -> "AI"
        pkg.contains("facebook") || pkg.contains("instagram") ||
        pkg.contains("twitter") || pkg.contains("linkedin") -> "Social"
        pkg.contains("maps") || pkg.contains("uber") ||
        pkg.contains("ola") || pkg.contains("rapido") -> "Navigation"
        pkg.contains("camera") || pkg.contains("gallery") ||
        pkg.contains("photo") -> "Camera"
        pkg.contains("clock") || pkg.contains("calendar") ||
        pkg.contains("alarm") || pkg.contains("notes") -> "Productivity"
        pkg.contains("bank") || pkg.contains("pay") ||
        pkg.contains("wallet") || pkg.contains("upi") -> "Finance"
        else -> "General"
    }
}
