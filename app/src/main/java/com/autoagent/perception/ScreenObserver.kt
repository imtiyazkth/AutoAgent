package com.autoagent.personal.perception

import com.autoagent.personal.service.accessibility.AutoAgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenObserver @Inject constructor(
    private val parser: AccessibilityTreeParser
) {
    private var lastSnapshot: UiSnapshot? = null

    fun observe(): UiSnapshot {
        val svc = AutoAgentAccessibilityService.getInstance()
        val root = svc?.rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: ""
        val snap = parser.parse(root, pkg)
        lastSnapshot = snap
        return snap
    }

    fun lastObserved(): UiSnapshot? = lastSnapshot

    fun hasScreenChanged(): Boolean {
        val fresh = observe()
        return fresh.fingerprint != lastSnapshot?.fingerprint
    }

    fun waitForText(text: String, timeoutMs: Long = 8000, pollMs: Long = 400): UiSnapshot? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snap = observe()
            if (snap.hasText(text)) return snap
            Thread.sleep(pollMs)
        }
        return null
    }

    fun waitForPackage(pkg: String, timeoutMs: Long = 6000, pollMs: Long = 300): UiSnapshot? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snap = observe()
            if (snap.packageName == pkg) return snap
            Thread.sleep(pollMs)
        }
        return null
    }
}
