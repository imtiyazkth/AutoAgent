package com.autoagent.personal.actions

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.autoagent.personal.perception.UiSnapshot
import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityExecutor @Inject constructor() {

    private val TAG = "A11yExecutor"

    fun tap(target: UiTarget, snapshot: UiSnapshot): ActionResult {
        val svc = AutoAgentAccessibilityService.getInstance()
            ?: return ActionResult.blocked("Accessibility service not running")
        val t0 = System.currentTimeMillis()

        // Priority 1: viewId
        if (target.viewId != null) {
            val node = snapshot.findById(target.viewId)
            if (node != null) return performTap(svc, node, t0, UiMatchStrategy.VIEW_ID)
        }

        // Priority 2: exact text
        if (target.primaryText != null) {
            val node = snapshot.findClickableByText(target.primaryText)
                ?: snapshot.findByText(target.primaryText)
            if (node != null) return performTap(svc, node, t0, UiMatchStrategy.EXACT_TEXT)
        }

        // Priority 3: contentDescription
        if (target.contentDescription != null) {
            val node = snapshot.nodes.firstOrNull {
                it.contentDescription?.contains(target.contentDescription, ignoreCase = true) == true
            }
            if (node != null) return performTap(svc, node, t0, UiMatchStrategy.CONTENT_DESCRIPTION)
        }

        // Priority 4: role
        if (target.role != null) {
            val node = snapshot.findByRole(target.role)
            if (node != null) return performTap(svc, node, t0, UiMatchStrategy.SEMANTIC_ROLE)
        }

        // Priority 5: partial text
        if (target.primaryText != null) {
            val words = target.primaryText.split(" ").filter { it.length > 2 }
            for (word in words) {
                val node = snapshot.nodes.firstOrNull { n ->
                    n.clickable && (
                        n.text?.contains(word, ignoreCase = true) == true ||
                        n.contentDescription?.contains(word, ignoreCase = true) == true
                    )
                }
                if (node != null) return performTap(svc, node, t0, UiMatchStrategy.PARTIAL_TEXT)
            }
        }

        // Last resort: coordinate fallback
        if (target.fallbackCoordinate != null) {
            Log.w(TAG, "Using coordinate fallback for: ${target.primaryText}")
            val (x, y) = target.fallbackCoordinate
            val success = tapCoordinate(svc, x, y)
            val ms = System.currentTimeMillis() - t0
            return if (success) ActionResult.success("Coordinate fallback", ms = ms)
            else ActionResult.failed("Coordinate fallback failed", ms = ms)
        }

        return ActionResult.notFound(target.primaryText ?: "unknown")
    }

    fun typeText(text: String, snapshot: UiSnapshot): ActionResult {
        val svc = AutoAgentAccessibilityService.getInstance()
            ?: return ActionResult.blocked("Accessibility service not running")
        val t0 = System.currentTimeMillis()
        val root = svc.rootInActiveWindow ?: return ActionResult.failed("No active window")
        val edit = svc.findEditableNode(root)
            ?: return ActionResult.notFound("editable text field")
        edit.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        val ms = System.currentTimeMillis() - t0
        return if (ok) ActionResult.success("Typed ${text.length} chars",
            evidence = ActionEvidence(afterText = "[typed]"), ms = ms)
        else ActionResult.failed("Text input failed", ms = ms)
    }

    fun pressSearch(snapshot: UiSnapshot): ActionResult {
        val svc = AutoAgentAccessibilityService.getInstance()
            ?: return ActionResult.blocked("Accessibility service not running")
        val t0 = System.currentTimeMillis()
        val root = svc.rootInActiveWindow ?: return ActionResult.failed("No active window")
        val edit = svc.findEditableNode(root)
            ?: return ActionResult.notFound("editable field")
        val args = Bundle()
        args.putInt("action_code", android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)
        if (edit.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args))
            return ActionResult.success("Search via IME", ms = System.currentTimeMillis() - t0)
        val dm = svc.resources.displayMetrics
        val ok = tapCoordinate(svc, dm.widthPixels * 0.92f, dm.heightPixels * 0.875f)
        val ms = System.currentTimeMillis() - t0
        return if (ok) ActionResult.success("Search via keyboard tap", ms = ms)
        else ActionResult.failed("Search key not found", ms = ms)
    }

    fun scroll(direction: ScrollDirection): ActionResult {
        val svc = AutoAgentAccessibilityService.getInstance()
            ?: return ActionResult.blocked("Accessibility service not running")
        val dm = svc.resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        val path = Path().apply {
            when (direction) {
                ScrollDirection.DOWN  -> { moveTo(w/2, h*0.72f); lineTo(w/2, h*0.28f) }
                ScrollDirection.UP    -> { moveTo(w/2, h*0.28f); lineTo(w/2, h*0.72f) }
                ScrollDirection.LEFT  -> { moveTo(w*0.8f, h/2);  lineTo(w*0.2f, h/2) }
                ScrollDirection.RIGHT -> { moveTo(w*0.2f, h/2);  lineTo(w*0.8f, h/2) }
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build()
        val ok = svc.dispatchGesture(gesture, null, null)
        return if (ok) ActionResult.success("Scrolled $direction")
        else ActionResult.failed("Scroll failed")
    }

    private fun performTap(
        svc: AutoAgentAccessibilityService,
        node: UiNode,
        t0: Long,
        strategy: UiMatchStrategy
    ): ActionResult {
        val root = svc.rootInActiveWindow ?: return ActionResult.failed("No active window")
        val result = findAndTapNode(svc, root, node)
        val ms = System.currentTimeMillis() - t0
        return if (result) ActionResult.success("Tapped via $strategy: ${node.displayText.take(30)}",
            evidence = ActionEvidence(nodeFound = true), ms = ms)
        else ActionResult.failed("Tap failed via $strategy: ${node.displayText}", ms = ms)
    }

    private fun findAndTapNode(
        svc: AutoAgentAccessibilityService,
        root: AccessibilityNodeInfo,
        target: UiNode
    ): Boolean {
        val text = target.text ?: target.contentDescription
        if (text != null) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val node = nodes?.firstOrNull { it.isClickable && it.isEnabled }
                ?: nodes?.firstOrNull { it.isEnabled }
            if (node != null) {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                nodes.forEach { try { it.recycle() } catch (_: Exception) {} }
                return ok
            }
        }
        if (!target.bounds.isEmpty) {
            return tapCoordinate(svc, target.bounds.exactCenterX(), target.bounds.exactCenterY())
        }
        return false
    }

    fun tapCoordinate(svc: AutoAgentAccessibilityService, x: Float, y: Float): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build()
            svc.dispatchGesture(gesture, null, null)
        } catch (e: Exception) { Log.e(TAG, "Tap coord: ${e.message}"); false }
    }
}
