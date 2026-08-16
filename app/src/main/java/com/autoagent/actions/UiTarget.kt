package com.autoagent.personal.actions

import android.graphics.Rect

enum class UiRole {
    BUTTON, TEXT_FIELD, LABEL, IMAGE, LIST_ITEM,
    CHECKBOX, SWITCH, MENU_ITEM, SCROLL_VIEW,
    SEARCH_BAR, SEND_BUTTON, BACK_BUTTON, UNKNOWN
}

enum class UiMatchStrategy {
    VIEW_ID, EXACT_TEXT, CONTENT_DESCRIPTION, HINT,
    SEMANTIC_ROLE, PARTIAL_TEXT, NORMALIZED_TEXT,
    CLASS_AND_ROLE, COORDINATE_FALLBACK
}

data class UiNode(
    val nodeId: String,
    val viewId: String? = null,
    val packageName: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val hint: String? = null,
    val role: UiRole = UiRole.UNKNOWN,
    val editable: Boolean = false,
    val clickable: Boolean = false,
    val focusable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val checked: Boolean = false,
    val bounds: Rect = Rect(),
    val depth: Int = 0,
    val childCount: Int = 0
) {
    val displayText: String get() = text ?: contentDescription ?: hint ?: viewId ?: nodeId
    val isEmpty: Boolean get() = text.isNullOrBlank() && contentDescription.isNullOrBlank()
}

data class UiTarget(
    val primaryText: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val hint: String? = null,
    val role: UiRole? = null,
    val fallbackCoordinate: Pair<Float, Float>? = null,
    val packageName: String? = null
) {
    companion object {
        fun byText(text: String) = UiTarget(primaryText = text)
        fun byId(id: String) = UiTarget(viewId = id)
        fun byRole(role: UiRole) = UiTarget(role = role)
        fun byDescription(desc: String) = UiTarget(contentDescription = desc)
        fun withFallback(text: String, x: Float, y: Float) =
            UiTarget(primaryText = text, fallbackCoordinate = x to y)
    }
}
