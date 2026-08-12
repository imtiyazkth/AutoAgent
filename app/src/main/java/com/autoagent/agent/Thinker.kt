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
    object TapSearchBar : Action()
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
                Decision("${step.desc} launch", Action.Launch(step.target), step.desc)
        }

        Intent.TAP -> {
            if (screen.hasText(step.target) || screen.hasClickable(step.target))
                Decision("'${step.target}' mila — tap", Action.Tap(step.target), "Tap: ${step.target}")
            else
                Decision("'${step.target}' nahi mila — scroll", Action.Scroll, "Scroll")
        }

        Intent.TAP_SEARCH_BAR -> {
            // Try multiple search bar texts YouTube uses
            val searchTexts = listOf("Search YouTube", "Search", "search_bar", "Search...")
            val found = searchTexts.firstOrNull { screen.hasText(it) || screen.hasClickable(it) }
            if (found != null)
                Decision("Search bar mila: $found", Action.Tap(found), "Search bar tap")
            else
                Decision("Search bar dhundh raha hoon", Action.TapSearchBar, "Search bar tap")
        }

        Intent.TAP_FIRST_RESULT -> {
            val query = step.target
            val firstWord = query.split(" ").first()
            if (screen.hasText(firstWord) || screen.hasClickable(firstWord))
                Decision("Result mila — tap", Action.Tap(firstWord), "Play: $firstWord")
            else
                Decision("Result dhundh raha — scroll", Action.Scroll, "Scroll results")
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
