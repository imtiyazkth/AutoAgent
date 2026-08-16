package com.autoagent.personal.perception

import com.autoagent.personal.actions.UiNode
import com.autoagent.personal.actions.UiRole

data class UiSnapshot(
    val packageName: String,
    val activityName: String? = null,
    val nodes: List<UiNode> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val fingerprint: String = ""
) {
    val texts: List<String> get() = nodes.mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }
    val clickableNodes: List<UiNode> get() = nodes.filter { it.clickable && it.enabled }
    val editableNodes: List<UiNode> get() = nodes.filter { it.editable }
    val hasInput: Boolean get() = editableNodes.isNotEmpty()
    val scrollable: Boolean get() = nodes.any { it.scrollable }
    val topTexts: String get() = texts.take(8).joinToString(" | ")

    fun hasText(q: String) = nodes.any { node ->
        node.text?.contains(q, ignoreCase = true) == true ||
        node.contentDescription?.contains(q, ignoreCase = true) == true ||
        node.hint?.contains(q, ignoreCase = true) == true
    }

    fun findByText(q: String): UiNode? = nodes.firstOrNull { node ->
        node.text?.contains(q, ignoreCase = true) == true ||
        node.contentDescription?.contains(q, ignoreCase = true) == true
    }

    fun findClickableByText(q: String): UiNode? = clickableNodes.firstOrNull { node ->
        node.text?.contains(q, ignoreCase = true) == true ||
        node.contentDescription?.contains(q, ignoreCase = true) == true
    }

    fun findByRole(role: UiRole): UiNode? = nodes.firstOrNull { it.role == role }
    fun findAllByRole(role: UiRole): List<UiNode> = nodes.filter { it.role == role }
    fun findById(id: String): UiNode? = nodes.firstOrNull { it.viewId == id }
}
