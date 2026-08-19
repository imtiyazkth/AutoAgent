package com.autoagent.personal.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AutoAgent Accessibility Service
 *
 * STATE MACHINE (read carefully before touching instance management):
 *
 *   DISABLED ──(user enables in settings)──► CONNECTING
 *   CONNECTING ──(onServiceConnected fires)──► CONNECTED
 *   CONNECTED ──(system kills service)──► DISCONNECTED
 *   DISCONNECTED ──(system restarts)──► CONNECTING
 *   Any state ──(exception in onAccessibilityEvent)──► ERROR
 *
 * KEY RULE: `instance` is set to non-null ONLY in onServiceConnected().
 * It is set to null ONLY in onDestroy(). This is the single source of truth.
 * Do NOT set it anywhere else.
 *
 * THREAD SAFETY: All reads of `instance` must use the companion object
 * accessor `AutoAgentAccessibilityService.instance` which is @Volatile.
 * Do NOT cache the reference in a local val that survives a coroutine
 * suspension point — the service can die between suspension points.
 */
class AutoAgentAccessibilityService : AccessibilityService() {

    // ─── State Machine ────────────────────────────────────────────────────────

    enum class ServiceState {
        DISABLED,           // Not yet enabled in system settings
        CONNECTING,         // Enabled in settings, onServiceConnected not yet called
        CONNECTED,          // onServiceConnected fired, service is live
        DISCONNECTED,       // Was connected, system killed it
        ERROR               // Exception in event handling
    }

    companion object {
        private const val TAG = "AutoAgentService"

        // @Volatile ensures visibility across threads without locking overhead.
        // Reads are safe; writes happen only from the main thread (service lifecycle).
        @Volatile
        var instance: AutoAgentAccessibilityService? = null
            private set

        // StateFlow so the UI can observe state changes reactively.
        private val _state = MutableStateFlow(ServiceState.DISABLED)
        val state: StateFlow<ServiceState> = _state.asStateFlow()

        // Convenience accessor — always null-safe
        fun isConnected(): Boolean = instance != null && _state.value == ServiceState.CONNECTED

        // Called from MainActivity/SettingsViewModel when user navigates
        // to the accessibility settings page — marks the transition to CONNECTING
        // so the UI can show a spinner rather than "disabled".
        fun onUserOpenedSettings() {
            if (_state.value == ServiceState.DISABLED) {
                _state.value = ServiceState.CONNECTING
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        Log.i(TAG, "onServiceConnected — service is live")
        instance = this
        _state.value = ServiceState.CONNECTED

        // Configure what events we want
        serviceInfo = serviceInfo?.also { info ->
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            info.notificationTimeout = 100
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt — service interrupted")
        // Do NOT set instance = null here. The service is not dead yet.
        // The system may call onServiceConnected again shortly.
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy — service is being destroyed")
        instance = null
        _state.value = ServiceState.DISCONNECTED
        super.onDestroy()
    }

    // ─── Event Handling ───────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't process events reactively in this version.
        // AgentController polls the accessibility tree on demand.
        // This keeps the service lightweight and avoids event-ordering bugs.
    }

    // ─── Node Utilities (used by AgentController) ─────────────────────────────

    /**
     * Get the root node of the currently active window.
     * Returns null if the service is not connected or the window is unavailable.
     * CALLER IS RESPONSIBLE for calling recycle() on the returned node when done.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "getRootNode failed", e)
            _state.value = ServiceState.ERROR
            null
        }
    }

    /**
     * Find the first node matching [text] anywhere in the current window.
     * Searches by text content (case-insensitive), then by contentDescription.
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return try {
            root.findAccessibilityNodeInfosByText(text)?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "findNodeByText($text) failed", e)
            null
        }
    }

    /**
     * Find the first editable (input) node in the current window.
     * Used for text entry without needing a specific resource ID.
     */
    fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Find nodes by resource ID suffix (e.g. "send_button").
     * The full ID format is "com.package:id/send_button".
     */
    fun findNodeById(idSuffix: String): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return try {
            root.findAccessibilityNodeInfosByViewId(idSuffix)?.firstOrNull()
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Try with package prefix if bare ID fails
                    root.findAccessibilityNodeInfosByViewId(
                        "${currentPackageName()}:id/$idSuffix"
                    )?.firstOrNull()
                } else null
        } catch (e: Exception) {
            Log.e(TAG, "findNodeById($idSuffix) failed", e)
            null
        }
    }

    /**
     * Returns the package name of the currently focused app.
     */
    fun currentPackageName(): String? {
        return try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Collect all clickable text labels in the current window.
     * Used by Thinker to find matching UI targets without coordinates.
     */
    fun getClickableTexts(): List<String> {
        val root = getRootNode() ?: return emptyList()
        val result = mutableListOf<String>()
        collectClickable(root, result)
        return result
    }

    private fun collectClickable(node: AccessibilityNodeInfo, into: MutableList<String>) {
        if (node.isClickable) {
            val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (label != null) into.add(label)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickable(child, into)
        }
    }

    /**
     * Check whether a node with [text] exists anywhere in the current window.
     */
    fun hasText(text: String): Boolean {
        return findNodeByText(text) != null
    }

    /**
     * Check whether the current window has any editable (input) field.
     */
    fun hasEditableField(): Boolean {
        val root = getRootNode() ?: return false
        return findEditableNode(root) != null
    }

    /**
     * Launch an app by package name using an explicit Intent.
     * This does NOT require the accessibility service to be active —
     * it uses the standard Android launcher mechanism.
     */
    fun launchApp(context: android.content.Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp($packageName) failed", e)
            false
        }
    }
}
