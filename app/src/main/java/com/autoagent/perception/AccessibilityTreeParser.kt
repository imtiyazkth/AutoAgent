package com.autoagent.personal.perception

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.autoagent.personal.actions.UiNode
import com.autoagent.personal.actions.UiRole
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityTreeParser @Inject constructor() {

    companion object {
        private const val MAX_DEPTH = 30
        private const val MAX_NODES = 500
    }

    fun parse(root: AccessibilityNodeInfo?, pkg: String): UiSnapshot {
        if (root == null) return UiSnapshot(packageName = pkg)
        val nodes = mutableListOf<UiNode>()
        collectNodes(root, nodes, 0)
        val fingerprint = buildFingerprint(nodes)
        return UiSnapshot(
            packageName = pkg,
            nodes = nodes,
            fingerprint = fingerprint,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun collectNodes(node: AccessibilityNodeInfo, out: MutableList<UiNode>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_NODES) return
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val text = node.text?.toString()?.take(200)
            val desc = node.contentDescription?.toString()?.take(200)
            val hint = node.hintText?.toString()?.take(100)
            val viewId = node.viewIdResourceName?.substringAfterLast("/")
            val role = inferRole(node)
            out.add(UiNode(
                nodeId = "${depth}_${out.size}",
                viewId = viewId,
                packageName = node.packageName?.toString(),
                className = node.className?.toString()?.substringAfterLast("."),
                text = text?.takeIf { it.isNotBlank() },
                contentDescription = desc?.takeIf { it.isNotBlank() },
                hint = hint?.takeIf { it.isNotBlank() },
                role = role,
                editable = node.isEditable,
                clickable = node.isClickable,
                focusable = node.isFocusable,
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                selected = node.isSelected,
                checked = node.isChecked,
                bounds = bounds,
                depth = depth,
                childCount = node.childCount
            ))
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectNodes(child, out, depth + 1)
                child.recycle()
            }
        } catch (_: Exception) {}
    }

    private fun inferRole(node: AccessibilityNodeInfo): UiRole {
        val cls = node.className?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        return when {
            node.isEditable -> UiRole.TEXT_FIELD
            cls.contains("edittext") -> UiRole.TEXT_FIELD
            cls.contains("button") -> when {
                text.contains("send") || desc.contains("send") -> UiRole.SEND_BUTTON
                text.contains("back") || desc.contains("back") -> UiRole.BACK_BUTTON
                text.contains("search") || desc.contains("search") -> UiRole.SEARCH_BAR
                else -> UiRole.BUTTON
            }
            cls.contains("checkbox") -> UiRole.CHECKBOX
            cls.contains("switch") -> UiRole.SWITCH
            cls.contains("imageview") && node.isClickable -> UiRole.BUTTON
            cls.contains("recyclerview") || cls.contains("listview") -> UiRole.LIST_ITEM
            cls.contains("scrollview") -> UiRole.SCROLL_VIEW
            desc.contains("search") || text.contains("search") -> UiRole.SEARCH_BAR
            desc.contains("send") -> UiRole.SEND_BUTTON
            node.isClickable -> UiRole.BUTTON
            else -> UiRole.LABEL
        }
    }

    private fun buildFingerprint(nodes: List<UiNode>): String {
        val sig = nodes.take(20).joinToString("|") { "${it.className}:${it.text?.take(20)}" }
        return MessageDigest.getInstance("MD5")
            .digest(sig.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }
}
