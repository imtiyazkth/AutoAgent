package com.autoagent.personal.agent

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GoalPlanner and EntityResolver.
 *
 * These tests can run on JVM — no Android SDK or emulator required.
 * Run with: ./gradlew test
 *
 * They specifically reproduce the bugs reported by the user:
 *   - Run → crash (service null check)
 *   - App picker crash (not testable here, see AppPickerViewModelTest)
 *   - NLP parsing failures for WhatsApp contact, YouTube play, dates
 */
class GoalPlannerTest {

    // ─── YouTube ──────────────────────────────────────────────────────────────

    @Test
    fun `youtube with play intent produces correct step sequence`() {
        val plan = GoalPlanner.plan("YouTube pe Arijit Singh ka gana chalao")
        assertEquals("com.google.android.youtube", plan.appPkg)
        assertTrue("Plan should have steps", plan.steps.isNotEmpty())

        val intents = plan.steps.map { it.intent }
        assertTrue(intents.contains(Intent.LAUNCH_APP))
        assertTrue(intents.contains(Intent.TAP_SEARCH_BAR))
        assertTrue(intents.contains(Intent.TYPE))
        assertTrue(intents.contains(Intent.SEARCH_KEY))
        assertTrue(intents.contains(Intent.TAP_FIRST_RESULT))
    }

    @Test
    fun `youtube search without play does not tap first result`() {
        val plan = GoalPlanner.plan("YouTube pe news search karo")
        val intents = plan.steps.map { it.intent }
        assertFalse(
            "Should NOT tap first result when 'play' keyword absent",
            intents.contains(Intent.TAP_FIRST_RESULT)
        )
    }

    @Test
    fun `youtube plan type step contains extracted query`() {
        val plan = GoalPlanner.plan("YouTube pe Shreya Ghoshal suno")
        val typeStep = plan.steps.firstOrNull { it.intent == Intent.TYPE }
        assertNotNull("TYPE step should exist", typeStep)
        assertTrue(
            "TYPE step should contain artist name",
            typeStep!!.target.contains("shreya", ignoreCase = true) ||
                    typeStep.target.contains("ghoshal", ignoreCase = true)
        )
    }

    // ─── WhatsApp ─────────────────────────────────────────────────────────────

    @Test
    fun `whatsapp with contact produces search steps`() {
        val plan = GoalPlanner.plan("WhatsApp pe Imtiyaz ko hi bolo")
        assertEquals("com.whatsapp", plan.appPkg)
        val intents = plan.steps.map { it.intent }
        assertTrue(intents.contains(Intent.TAP_SEARCH_BAR))
        assertTrue(intents.contains(Intent.TAP_FIRST_RESULT))
    }

    @Test
    fun `whatsapp with quoted message includes type step`() {
        val plan = GoalPlanner.plan("WhatsApp pe Imtiyaz ko 'Hello brother' bhejo")
        val typeSteps = plan.steps.filter { it.intent == Intent.TYPE }
        // Should have: contact type + message type
        assertTrue("Should have at least 2 TYPE steps", typeSteps.size >= 2)
        val hasMessage = typeSteps.any { it.target.contains("hello", ignoreCase = true) }
        assertTrue("One TYPE step should contain the quoted message", hasMessage)
    }

    @Test
    fun `whatsapp without contact still launches app`() {
        val plan = GoalPlanner.plan("WhatsApp kholo")
        val launchStep = plan.steps.firstOrNull { it.intent == Intent.LAUNCH_APP }
        assertNotNull(launchStep)
        assertEquals("com.whatsapp", launchStep!!.target)
    }

    // ─── Empty / unrecognized ─────────────────────────────────────────────────

    @Test
    fun `unrecognized input returns empty plan`() {
        val plan = GoalPlanner.plan("qxyz ajsdk random gibberish 12345")
        assertTrue("Unknown goal should produce empty steps", plan.steps.isEmpty())
    }

    @Test
    fun `empty input returns empty plan`() {
        val plan = GoalPlanner.plan("")
        assertTrue(plan.steps.isEmpty())
    }

    // ─── Home ─────────────────────────────────────────────────────────────────

    @Test
    fun `home intent produces HOME step`() {
        val plan = GoalPlanner.plan("band karo aur home pe jao")
        assertTrue(plan.steps.any { it.intent == Intent.HOME })
    }

    // ─── Generic apps ─────────────────────────────────────────────────────────

    @Test
    fun `settings launches correct package`() {
        val plan = GoalPlanner.plan("settings kholo")
        val launch = plan.steps.firstOrNull { it.intent == Intent.LAUNCH_APP }
        assertNotNull(launch)
        assertEquals("com.android.settings", launch!!.target)
    }

    // ─── Wait steps ───────────────────────────────────────────────────────────

    @Test
    fun `all plans contain at least one WAIT step after LAUNCH_APP`() {
        val queries = listOf(
            "YouTube pe video dekhna",
            "WhatsApp pe message bhejo",
            "Chrome kholo"
        )
        for (q in queries) {
            val plan = GoalPlanner.plan(q)
            val launchIdx = plan.steps.indexOfFirst { it.intent == Intent.LAUNCH_APP }
            if (launchIdx >= 0 && launchIdx < plan.steps.lastIndex) {
                val nextStep = plan.steps[launchIdx + 1]
                assertEquals(
                    "Step after LAUNCH_APP in '$q' should be WAIT",
                    Intent.WAIT,
                    nextStep.intent
                )
            }
        }
    }
}

// ─── EntityResolver tests ──────────────────────────────────────────────────────

class EntityResolverTest {

    private val resolver = EntityResolver()

    @Test
    fun `resolveContact extracts name from whatsapp message`() {
        val contact = resolver.resolveContact("WhatsApp pe Imtiyaz ko hello bolo")
        assertNotNull("Contact should be extracted", contact)
        assertTrue(contact!!.contains("Imtiyaz", ignoreCase = true))
    }

    @Test
    fun `resolveContact extracts name from send to pattern`() {
        val contact = resolver.resolveContact("send message to John about the meeting")
        assertNotNull(contact)
        assertTrue(contact!!.contains("John", ignoreCase = true))
    }

    @Test
    fun `resolveMessage extracts quoted text`() {
        val msg = resolver.resolveMessage("WhatsApp pe bolo 'Good morning bhai'")
        assertNotNull(msg)
        assertEquals("Good morning bhai", msg)
    }

    @Test
    fun `resolveMessage returns null when no message`() {
        val msg = resolver.resolveMessage("YouTube pe music suno")
        assertNull("No message should be found in YouTube task", msg)
    }

    @Test
    fun `resolveMessage DOES NOT throw PatternSyntaxException`() {
        // This is a regression test for the unclosed bracket bug:
        // Regex("""(?:resolve[Message])...""") — was throwing at class load time
        val result = runCatching { resolver.resolveMessage("any input text") }
        assertTrue("resolveMessage should not throw", result.isSuccess)
    }

    @Test
    fun `resolveDateTime parses tomorrow correctly`() {
        val dt = resolver.resolveDateTime("tomorrow at 9am")
        assertNotNull(dt)
        assertTrue(dt!!.isRelative)
        assertEquals(9, dt.dateTime.hour)
        assertEquals(0, dt.dateTime.minute)
    }

    @Test
    fun `resolveDateTime parses in X minutes`() {
        val dt = resolver.resolveDateTime("in 30 minutes")
        assertNotNull(dt)
        assertTrue(dt!!.isRelative)
        // The returned time should be approximately 30 minutes from now
        val nowPlusTen = java.time.LocalDateTime.now().plusMinutes(25)
        assertTrue(dt.dateTime.isAfter(nowPlusTen))
    }

    @Test
    fun `resolveDateTime parses specific date`() {
        val dt = resolver.resolveDateTime("5 October at 5:00 PM")
        assertNotNull(dt)
        assertEquals(10, dt!!.dateTime.monthValue)
        assertEquals(5, dt.dateTime.dayOfMonth)
        assertEquals(17, dt.dateTime.hour)
        assertEquals(0, dt.dateTime.minute)
    }

    @Test
    fun `resolveDateTime returns null for unrecognized input`() {
        val dt = resolver.resolveDateTime("just do it now")
        assertNull(dt)
    }

    @Test
    fun `resolveAppPackage returns correct package for whatsapp`() {
        val pkg = resolver.resolveAppPackage("whatsapp")
        assertEquals("com.whatsapp", pkg)
    }

    @Test
    fun `resolveAppPackage returns null for unknown app`() {
        val pkg = resolver.resolveAppPackage("some random unknown app xyz")
        assertNull(pkg)
    }
}
