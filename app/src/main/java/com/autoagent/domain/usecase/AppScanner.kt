package com.autoagent.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.autoagent.domain.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AppScanner"
    }

    // Must be called on Dispatchers.IO — never on Main
    fun scanInstalledApps(): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager

            // Use CATEGORY_LAUNCHER — works without QUERY_ALL_PACKAGES
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchableSet: Set<String> = try {
                pm.queryIntentActivities(launchIntent, 0)
                    .mapNotNull { ri -> ri.activityInfo?.packageName?.takeIf { it.isNotBlank() } }
                    .toSet()
            } catch (e: Exception) {
                Log.e(TAG, "queryIntentActivities failed: ${e.message}")
                emptySet()
            }

            Log.d(TAG, "Found ${launchableSet.size} launchable packages")
            if (launchableSet.isEmpty()) return emptyList()

            // Map each package individually — per-item try/catch prevents one bad
            // entry from crashing the whole scan (MIUI returns null applicationInfo)
            launchableSet.mapNotNull { pkg ->
                buildAppInfo(pm, pkg)
            }.sortedBy { it.appName }

        } catch (e: Exception) {
            Log.e(TAG, "scanInstalledApps failed: ${e.message}")
            emptyList()
        }
    }

    private fun buildAppInfo(pm: PackageManager, packageName: String): InstalledAppInfo? {
        return try {
            // getApplicationInfo works even without QUERY_ALL_PACKAGES for launchable apps
            val appInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName,
                        PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Package not found: $packageName")
                return null
            }

            val appName = try {
                pm.getApplicationLabel(appInfo).toString().trim().ifBlank { packageName }
            } catch (e: Exception) { packageName }

            val pkgInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
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
            Log.w(TAG, "buildAppInfo failed for $packageName: ${e.message}")
            null  // skip — never crash the whole scan
        }
    }

    private fun detectCategory(pkg: String): String = when {
        pkg.contains("whatsapp") || pkg.contains("telegram") ||
        pkg.contains("signal") || pkg.contains("viber") -> "Messaging"
        pkg.contains("facebook") || pkg.contains("instagram") ||
        pkg.contains("twitter") || pkg.contains("linkedin") ||
        pkg.contains("snapchat") || pkg.contains("tiktok") -> "Social"
        pkg.contains("chrome") || pkg.contains("firefox") ||
        pkg.contains("opera") || pkg.contains("brave") -> "Browser"
        pkg.contains("youtube") || pkg.contains("netflix") ||
        pkg.contains("spotify") || pkg.contains("gaana") -> "Media"
        pkg.contains("gmail") || pkg.contains("outlook") ||
        pkg.contains("mail") || pkg.contains("yahoo") -> "Email"
        pkg.contains("maps") || pkg.contains("uber") ||
        pkg.contains("ola") || pkg.contains("rapido") -> "Navigation"
        pkg.contains("openai") || pkg.contains("claude") ||
        pkg.contains("gemini") || pkg.contains("copilot") -> "AI"
        pkg.contains("bank") || pkg.contains("pay") ||
        pkg.contains("wallet") || pkg.contains("phonepe") ||
        pkg.contains("gpay") || pkg.contains("paytm") -> "Finance"
        pkg.contains("camera") || pkg.contains("gallery") ||
        pkg.contains("photo") || pkg.contains("snapseed") -> "Camera"
        pkg.contains("zomato") || pkg.contains("swiggy") ||
        pkg.contains("blinkit") || pkg.contains("bigbasket") -> "Food"
        pkg.contains("amazon") || pkg.contains("flipkart") ||
        pkg.contains("myntra") || pkg.contains("meesho") -> "Shopping"
        pkg.contains("zoom") || pkg.contains("teams") ||
        pkg.contains("meet") || pkg.contains("skype") -> "Video Call"
        else -> "General"
    }
}
