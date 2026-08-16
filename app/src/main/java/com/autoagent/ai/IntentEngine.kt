package com.autoagent.personal.ai

import javax.inject.Inject
import javax.inject.Singleton

enum class ParsedIntent {
    OPEN_APP, SEARCH_AND_PLAY_MEDIA, SEARCH_UI, SEND_MESSAGE,
    MAKE_CALL, SCHEDULE_TASK, LOCK_SCREEN, ANSWER_CALL,
    DECLINE_CALL, TOGGLE_SPEAKER, TAKE_SCREENSHOT, TOGGLE_WIFI,
    TOGGLE_BLUETOOTH, GO_HOME, CLOSE_APP, STOP_AGENT, UNKNOWN
}

data class ResolvedEntity(
    val type: EntityType,
    val value: String,
    val confidence: Float = 1.0f
)

enum class EntityType {
    APP_NAME, PACKAGE_NAME, QUERY, CONTACT,
    MESSAGE, TIME, DATE, RECIPIENT_LIST, URL, UNKNOWN
}

data class ParsedCommand(
    val raw: String,
    val intent: ParsedIntent,
    val entities: List<ResolvedEntity>,
    val confidence: Float,
    val targetApp: String? = null,
    val targetPkg: String? = null
) {
    fun entity(type: EntityType): ResolvedEntity? = entities.firstOrNull { it.type == type }
    fun query()   = entity(EntityType.QUERY)?.value
    fun contact() = entity(EntityType.CONTACT)?.value
    fun message() = entity(EntityType.MESSAGE)?.value
}

@Singleton
class IntentEngine @Inject constructor(
    private val entityResolver: EntityResolver
) {
    fun parse(input: String): ParsedCommand {
        val g = input.lowercase().trim()
        val entities = mutableListOf<ResolvedEntity>()

        val intent = when {
            g.hasAny("stop","band karo agent","rok","bas karo","emergency stop") -> ParsedIntent.STOP_AGENT
            g.hasAny("lock karo","screen lock","mobile lock","phone lock")       -> ParsedIntent.LOCK_SCREEN
            g.hasAny("call uthao","call receive","answer karo","phone uthao")    -> ParsedIntent.ANSWER_CALL
            g.hasAny("call cut","decline","call kat","reject karo")              -> ParsedIntent.DECLINE_CALL
            g.hasAny("speaker","speakerphone","handfree","loudspeaker")          -> ParsedIntent.TOGGLE_SPEAKER
            g.hasAny("screenshot","screen shot")                                 -> ParsedIntent.TAKE_SCREENSHOT
            g.hasAny("wifi")                                                     -> ParsedIntent.TOGGLE_WIFI
            g.hasAny("bluetooth")                                                -> ParsedIntent.TOGGLE_BLUETOOTH
            g.hasAny("home pe jao","ghar jao","home jao","go home")             -> ParsedIntent.GO_HOME
            g.hasAny("band karo","close karo","sab band")                       -> ParsedIntent.CLOSE_APP
            g.hasAny("whatsapp","telegram","message bhejo","text karo")         -> ParsedIntent.SEND_MESSAGE
            g.hasAny("call karo","phone karo","ring karo")                      -> ParsedIntent.MAKE_CALL
            g.hasAny("youtube","spotify","music","gana","video","play")         -> ParsedIntent.SEARCH_AND_PLAY_MEDIA
            g.hasAny("search karo","dhundo","khojo")                            -> ParsedIntent.SEARCH_UI
            g.hasAny("kholo","open karo","launch karo")                         -> ParsedIntent.OPEN_APP
            g.hasAny("schedule","kal","subah","baje")                           -> ParsedIntent.SCHEDULE_TASK
            else -> ParsedIntent.UNKNOWN
        }

        val (appName, appPkg) = entityResolver.resolveApp(g)
        if (appPkg != null) {
            entities.add(ResolvedEntity(EntityType.APP_NAME, appName ?: ""))
            entities.add(ResolvedEntity(EntityType.PACKAGE_NAME, appPkg))
        }

        val query = entityResolver.resolveQuery(g, appName)
        if (query != null) entities.add(ResolvedEntity(EntityType.QUERY, query))

        val contact = entityResolver.resolveContact(g)
        if (contact != null) entities.add(ResolvedEntity(EntityType.CONTACT, contact))

        val message = entityResolver.resolveMessage(g)
        if (message != null) entities.add(ResolvedEntity(EntityType.MESSAGE, message))

        val confidence = when {
            intent == ParsedIntent.UNKNOWN -> 0.3f
            appPkg != null && query != null -> 0.92f
            appPkg != null -> 0.85f
            intent != ParsedIntent.UNKNOWN -> 0.75f
            else -> 0.5f
        }

        return ParsedCommand(input, intent, entities, confidence, appName, appPkg)
    }

    private fun String.hasAny(vararg words: String) = words.any { this.contains(it) }
}
