package com.autoagent.personal.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.autoagent.personal.domain.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object { private const val TAG = "AppScanner" }

    fun scanInstalledApps(): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchableSet: Set<String> = try {
                pm.queryIntentActivities(launchIntent, 0)
                    .mapNotNull { ri ->
                        try { ri.activityInfo?.packageName?.takeIf { it.isNotBlank() } }
                        catch (e: Exception) { null }
                    }
                    .toSet()
            } catch (e: Exception) {
                Log.e(TAG, "queryIntentActivities failed: ${e.message}")
                emptySet()
            }
            Log.d(TAG, "launchable packages: ${launchableSet.size}")
            if (launchableSet.isEmpty()) return emptyList()
            launchableSet.mapNotNull { pkg -> buildSafe(pm, pkg) }
                .sortedBy { it.appName }
        } catch (e: Exception) {
            Log.e(TAG, "scan total failure: ${e.message}")
            emptyList()
        }
    }

    private fun buildSafe(pm: PackageManager, packageName: String): InstalledAppInfo? {
        return try {
            val appInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    pm.getApplicationInfo(packageName,
                        PackageManager.ApplicationInfoFlags.of(0))
                else
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) { return null }

            val appName = try {
                pm.getApplicationLabel(appInfo).toString().trim().ifBlank { packageName }
            } catch (e: Exception) { packageName }

            val pkgInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                else
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
            } catch (e: Exception) { null }

            InstalledAppInfo(
                packageName = packageName,
                appName = appName,
                versionName = pkgInfo?.versionName ?: "?",
                installDate = pkgInfo?.firstInstallTime ?: 0L,
                lastUpdated = pkgInfo?.lastUpdateTime ?: 0L,
                canLaunch = true,
                category = detectCategory(packageName),
                launchActivity = try {
                    pm.getLaunchIntentForPackage(packageName)?.component?.className
                } catch (e: Exception) { null }
            )
        } catch (e: Exception) {
            Log.w(TAG, "skip $packageName: ${e.message}")
            null
        }
    }

    private fun detectCategory(pkg: String): String = when {
        pkg.contains("whatsapp") || pkg.contains("telegram") ||
        pkg.contains("signal") || pkg.contains("viber") -> "Messaging"
        pkg.contains("facebook") || pkg.contains("instagram") ||
        pkg.contains("twitter") || pkg.contains("linkedin") ||
        pkg.contains("snapchat") -> "Social"
        pkg.contains("chrome") || pkg.contains("firefox") ||
        pkg.contains("opera") || pkg.contains("brave") -> "Browser"
        pkg.contains("youtube") || pkg.contains("netflix") ||
        pkg.contains("spotify") || pkg.contains("gaana") -> "Media"
        pkg.contains("gmail") || pkg.contains("outlook") ||
        pkg.contains("mail") -> "Email"
        pkg.contains("maps") || pkg.contains("uber") ||
        pkg.contains("ola") || pkg.contains("rapido") -> "Navigation"
        pkg.contains("bank") || pkg.contains("pay") ||
        pkg.contains("phonepe") || pkg.contains("paytm") -> "Finance"
        pkg.contains("zomato") || pkg.contains("swiggy") -> "Food"
        pkg.contains("amazon") || pkg.contains("flipkart") -> "Shopping"
        pkg.contains("zoom") || pkg.contains("teams") ||
        pkg.contains("meet") -> "Video Call"
        pkg.contains("claude") || pkg.contains("openai") ||
        pkg.contains("gemini") -> "AI"
        else -> "General"
    }
}
