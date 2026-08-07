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

    fun scanInstalledApps(): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager

            // Step 1: get launchable packages via CATEGORY_LAUNCHER
            // This does NOT require QUERY_ALL_PACKAGES
            val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchable: Set<String> = try {
                pm.queryIntentActivities(launchIntent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()
            } catch (e: Exception) {
                Log.e(TAG, "queryIntentActivities failed: ${e.message}")
                emptySet()
            }

            Log.d(TAG, "Found ${launchable.size} launchable packages")

            if (launchable.isEmpty()) {
                Log.w(TAG, "No launchable apps found — returning empty list")
                return emptyList()
            }

            // Step 2: get package details only for launchable apps
            val packages = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "getInstalledPackages SecurityException: ${e.message}")
                // Fallback: build list from launchable only
                return launchable.mapNotNull { pkg ->
                    buildAppInfoFromPackageName(pm, pkg)
                }.sortedBy { it.appName }
            } catch (e: Exception) {
                Log.e(TAG, "getInstalledPackages failed: ${e.message}")
                return launchable.mapNotNull { pkg ->
                    buildAppInfoFromPackageName(pm, pkg)
                }.sortedBy { it.appName }
            }

            // Step 3: filter + map with per-item null safety
            packages
                .filter { launchable.contains(it.packageName) }
                .filter { it.applicationInfo != null } // CRITICAL: null guard for MIUI
                .mapNotNull { pkg ->
                    try {
                        val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                        InstalledAppInfo(
                            packageName = pkg.packageName,
                            appName = try {
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) { pkg.packageName },
                            versionName = pkg.versionName ?: "1.0",
                            installDate = pkg.firstInstallTime,
                            lastUpdated = pkg.lastUpdateTime,
                            canLaunch = true,
                            category = detectCategory(pkg.packageName),
                            launchActivity = try {
                                pm.getLaunchIntentForPackage(pkg.packageName)
                                    ?.component?.className
                            } catch (e: Exception) { null }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping ${pkg.packageName}: ${e.message}")
                        null // skip broken entries silently
                    }
                }
                .sortedBy { it.appName }

        } catch (e: Exception) {
            Log.e(TAG, "scanInstalledApps total failure: ${e.message}")
            emptyList()
        }
    }

    // Fallback: build InstalledAppInfo just from package name
    private fun buildAppInfoFromPackageName(
        pm: PackageManager,
        packageName: String
    ): InstalledAppInfo? {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            InstalledAppInfo(
                packageName = packageName,
                appName = pm.getApplicationLabel(appInfo).toString(),
                versionName = "?",
                installDate = 0L,
                lastUpdated = 0L,
                canLaunch = true,
                category = detectCategory(packageName),
                launchActivity = try {
                    pm.getLaunchIntentForPackage(packageName)?.component?.className
                } catch (e: Exception) { null }
            )
        } catch (e: Exception) {
            Log.w(TAG, "buildAppInfoFromPackageName failed for $packageName: ${e.message}")
            null
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
