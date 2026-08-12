package com.autoagent.personal.agent

data class Decision(
    val thought: String,
    val action: Action,
    val description: String,
    val isDone: Boolean = false,
    val skip: Boolean = false
)

sealed class Action {
    object None : Action()
    data class Launch(val pkg: String) : Action()
    data class Tap(val text: String) : Action()
    data class TapSearchBar(val appHint: String) : Action()
    data class TapFirstResult(val query: String) : Action()
    data class Type(val text: String) : Action()
    object SearchKey : Action()
    object Scroll : Action()
    data class Wait(val ms: Long) : Action()
    object Home : Action()
    object Back : Action()
}

object Thinker {
    fun decide(step: Step, screen: ScreenState): Decision = when (step.intent) {

        Intent.LAUNCH_APP -> {
            if (screen.pkg == step.target)
                Decision("App already open", Action.None, "Skip", skip = true)
            else
                Decision(step.desc, Action.Launch(step.target), step.desc)
        }

        Intent.TAP -> {
            if (screen.hasText(step.target) || screen.hasClickable(step.target))
                Decision("'${step.target}' mila", Action.Tap(step.target), "Tap: ${step.target}")
            else
                Decision("'${step.target}' nahi mila — scroll", Action.Scroll, "Scroll")
        }

        Intent.TAP_SEARCH_BAR ->
            Decision("Search bar tap", Action.TapSearchBar(step.target), "Search bar tap")

        Intent.TAP_FIRST_RESULT -> {
            // Try text-based tap from screen first
            val queryWords = step.target.split(" ").filter { it.length > 2 }
            val matchedText = queryWords.firstOrNull { w ->
                screen.clickable.any { it.contains(w, ignoreCase = true) }
            }
            if (matchedText != null) {
                val clickableText = screen.clickable.first { it.contains(matchedText, ignoreCase = true) }
                Decision("Result mila: $clickableText", Action.Tap(clickableText), "Tap: $clickableText")
            } else {
                Decision("Result tap by coordinate", Action.TapFirstResult(step.target), "Tap first result")
            }
        }

        Intent.TYPE -> {
            if (screen.hasInput)
                Decision("Type: ${step.target}", Action.Type(step.target), "Type: ${step.target}")
            else
                Decision("Input nahi mila — wait", Action.Wait(1500), "Wait for input")
        }

        Intent.SEARCH_KEY -> Decision("Search!", Action.SearchKey, "Search")
        Intent.SCROLL     -> Decision("Scroll", Action.Scroll, "Scroll")
        Intent.WAIT       -> Decision("Wait", Action.Wait(step.target.toLongOrNull() ?: 1000L), "Wait")
        Intent.HOME       -> Decision("Home", Action.Home, "Home")
        Intent.BACK       -> Decision("Back", Action.Back, "Back")
    }
}
