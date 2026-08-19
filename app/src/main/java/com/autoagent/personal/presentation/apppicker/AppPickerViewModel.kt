package com.autoagent.personal.presentation.apppicker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─── Data Model ───────────────────────────────────────────────────────────────

/**
 * AppInfo is IMMUTABLE. This is critical for Compose stability.
 * Never use `var` fields in data classes that live inside StateFlow.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?         // May be null while loading; Compose handles null gracefully
)

data class AppPickerUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppInfo> = emptyList(),       // Full list — never filtered in VM state
    val filteredApps: List<AppInfo> = emptyList(),  // Filtered list for display
    val searchQuery: String = "",
    val selectedApp: AppInfo? = null,
    val error: String? = null
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * AppPickerViewModel
 *
 * CRASH FIX — Root causes of the previous crashes:
 *
 * 1. CRASH on open:
 *    `var apps by mutableStateOf<List<AppInfo>>(emptyList())` was declared
 *    at the Composable level. Setting it from `withContext(Dispatchers.IO)` triggers
 *    a Compose state write from a non-main thread → ConcurrentModificationException
 *    or snapshot-layer crash.
 *
 *    Fix: All list state lives in this ViewModel's StateFlow. Compose only reads.
 *
 * 2. CRASH on typing into search:
 *    The `filter()` call on a live mutable list while `LazyColumn` was in mid-composition
 *    caused IndexOutOfBoundsException.
 *
 *    Fix: Filtering is done here in the ViewModel on Dispatchers.Default, then emitted
 *    as a new immutable list via StateFlow. LazyColumn always sees a stable snapshot.
 *
 * 3. CRASH on large icon loading:
 *    Loading `Drawable` for 200+ apps on the main thread caused ANR.
 *
 *    Fix: Icons are loaded on Dispatchers.IO in batches. Apps appear immediately
 *    without icons, icons fill in asynchronously.
 */
@HiltViewModel
class AppPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "AppPickerViewModel"

    private val _uiState = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadInstalledApps()
        observeSearch()
    }

    // ─── App loading ──────────────────────────────────────────────────────────

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Step 1: Load names quickly (no icons) — show list ASAP
                val appsWithoutIcons = withContext(Dispatchers.IO) {
                    queryInstalledApps(context, includeIcons = false)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        allApps = appsWithoutIcons,
                        filteredApps = appsWithoutIcons
                    )
                }

                // Step 2: Load icons in background — update list when done
                val appsWithIcons = withContext(Dispatchers.IO) {
                    queryInstalledApps(context, includeIcons = true)
                }

                _uiState.update { state ->
                    val query = state.searchQuery
                    val filtered = if (query.isBlank()) appsWithIcons
                    else appsWithIcons.filter { it.matchesQuery(query) }
                    state.copy(allApps = appsWithIcons, filteredApps = filtered)
                }

            } catch (e: Exception) {
                Log.e(TAG, "loadInstalledApps error", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Apps load nahi hue: ${e.message}")
                }
            }
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(150)  // Debounce so we don't filter on every keystroke
                .collect { query ->
                    val allApps = _uiState.value.allApps
                    val filtered = withContext(Dispatchers.Default) {
                        if (query.isBlank()) allApps
                        else allApps.filter { it.matchesQuery(query) }
                    }
                    _uiState.update { it.copy(searchQuery = query, filteredApps = filtered) }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query  // This is the ONLY place that writes to _searchQuery
    }

    // ─── Selection ────────────────────────────────────────────────────────────

    fun selectApp(app: AppInfo) {
        _uiState.update { it.copy(selectedApp = app) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedApp = null) }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun queryInstalledApps(context: Context, includeIcons: Boolean): List<AppInfo> {
        val pm = context.packageManager

        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        } else {
            null
        }

        @Suppress("DEPRECATION")
        val allPackages: List<ApplicationInfo> = if (flags != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(flags)
        } else {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        return allPackages
            .filter { appInfo ->
                // Only show apps that have a launcher intent (user-facing apps)
                pm.getLaunchIntentForPackage(appInfo.packageName) != null
            }
            .mapNotNull { appInfo ->
                try {
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val icon = if (includeIcons) {
                        runCatching { pm.getApplicationIcon(appInfo.packageName) }.getOrNull()
                    } else null
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = name,
                        icon = icon
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping ${appInfo.packageName}: ${e.message}")
                    null // Skip apps that throw during label/icon resolution
                }
            }
            .sortedBy { it.appName.lowercase() }
    }

    private fun AppInfo.matchesQuery(query: String): Boolean {
        val q = query.lowercase().trim()
        return appName.lowercase().contains(q) || packageName.lowercase().contains(q)
    }
}
