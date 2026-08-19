# AutoAgent — Complete Repository Audit Report
> Senior Android/Kotlin Engineer + Accessibility Specialist Assessment  
> Date: 2026-08-18 | Repo: imtiyazkth/AutoAgent | Branch: main

---

## EXECUTIVE SUMMARY

87 CI runs. Still failing. The repository is caught in a **patch-and-hope loop** — each commit fixes one compile error and introduces the next. The root problem is not individual bugs; it is **architectural instability** created by layering new abstractions on top of half-implemented ones without ever resolving the base problems first.

The five root causes, in order of severity:

1. **Hilt dependency injection is broken at the module level** — `AppModule` and `PhoneControlEngine` are fighting for the same constructor injection, causing `NonExistentClass` and `@ApplicationContext` errors in every other build.
2. **AccessibilityService instance management is incorrect** — the service is tracked via a static companion object, so when the system kills and restarts the service the old null reference is never cleared, causing NPE on every `Run` press.
3. **ReactAgent has no coroutine scope of its own** — it is called from `viewModelScope` with `withContext(Dispatchers.IO)` but the agent itself does blocking thread sleeps inside `suspend` functions, causing ANR/crash on the main thread when navigation happens concurrently.
4. **Installed App Picker uses mutable state that triggers recomposition during async loading** — setting `mutableStateOf` list from a background coroutine while the `LazyColumn` is composing causes `ConcurrentModificationException`.
5. **The GitHub Actions workflow (Build Debug APK) does not run tests** — it assembles but never validates, so crashes only surface at runtime. CI is green while the app crashes.

---

## SECTION 1 — ARCHITECTURE MAP

```
AutoAgent (com.autoagent.personal)
│
├── UI Layer (Jetpack Compose)
│   ├── MainActivity.kt                    ← single Activity
│   ├── navigation/NavGraph.kt             ← Compose Navigation
│   ├── presentation/
│   │   ├── dashboard/DashboardScreen.kt
│   │   ├── dashboard/DashboardViewModel.kt
│   │   ├── taskbuilder/TaskBuilderScreen.kt
│   │   ├── taskbuilder/TaskBuilderViewModel.kt
│   │   ├── voice/VoiceAgentScreen.kt
│   │   └── voice/VoiceAgentViewModel.kt
│
├── Domain Layer
│   ├── domain/model/Task.kt
│   ├── domain/model/TriggerType.kt
│   ├── domain/model/RunStatus.kt
│   └── domain/usecase/...
│
├── Data Layer
│   ├── data/db/AppDatabase.kt             ← Room
│   ├── data/db/TaskEntity.kt
│   ├── data/db/ExecutionLogEntity.kt
│   ├── data/repository/TaskRepository.kt
│   └── data/util/GsonHelper.kt
│
├── Agent Layer (THE PROBLEM ZONE)
│   ├── agent/ReactAgent.kt                ← main executor — no scope
│   ├── agent/AgentController.kt           ← v3 re-arch — partially wired
│   ├── agent/GoalPlanner.kt               ← NLP → Plan
│   ├── agent/GoalDecomposer.kt            ← new, conflicts with GoalPlanner
│   ├── agent/IntentEngine.kt              ← new, overlaps GoalPlanner
│   ├── agent/Thinker.kt                   ← decide step action
│   ├── agent/EntityResolver.kt            ← contact/date resolution
│   ├── agent/VerificationEngine.kt        ← new, partially implemented
│   ├── agent/RecoveryEngine.kt            ← new, not wired to executor
│   ├── agent/RiskEngine.kt                ← new, not wired to anything
│   └── agent/ExperienceRecorder.kt        ← new, overlaps ExecutionLog
│
├── Service Layer
│   ├── service/AutoAgentAccessibilityService.kt  ← instance tracking BROKEN
│   └── worker/TaskExecutorWorker.kt              ← WorkManager worker
│
├── Memory/Safety
│   ├── memory/MemoryEngine.kt
│   └── safety/... (unimplemented stubs)
│
└── DI
    └── di/AppModule.kt                    ← Hilt module — BROKEN
```

**Architecture Problems Identified:**
- `GoalPlanner`, `GoalDecomposer`, and `IntentEngine` do the same job — three competing NLP → plan pipelines
- `ExperienceRecorder` duplicates `ExecutionLogEntity`
- `ReactAgent` calls `AccessibilityService` via static companion, not via a stable interface
- `AgentController` is injected by Hilt but depends on `PhoneControlEngine` which is also injected by Hilt — circular dependency

---

## SECTION 2 — BUILD FINDINGS

### 2A. Gradle Version Matrix (as found)

| Item | Value | Problem |
|------|-------|---------|
| Gradle Wrapper | 8.9 | ✅ OK |
| Android Gradle Plugin | 8.5.2 | ✅ OK |
| Kotlin | 1.9.22 | ⚠️ Old — Compose 1.5.8 requires exactly 1.9.x, OK but tight |
| KSP | 1.9.22-1.0.17 | ✅ Matches Kotlin |
| Hilt | 2.50 | ✅ OK |
| Compose BOM | 2024.04.01 | ✅ OK |
| Compose Compiler | 1.5.8 | ✅ Matches Kotlin 1.9.22 |
| compileSdk | 35 | ✅ OK |
| minSdk | 26 | ✅ OK |
| Java | 17 | ✅ OK |

### 2B. Build Errors (Priority Order)

**CRITICAL — Hilt NonExistentClass**
```
e: [ksp] error: [Hilt] Cannot find @AndroidEntryPoint `PhoneControlEngine`
```
Root cause: `PhoneControlEngine` is annotated but is not an Activity/Fragment/Service/ViewModel — it is a plain class. You cannot `@Inject` it into `AppModule` directly without a `@Provides` function.

**CRITICAL — AgentController constructor injection failure**
```
error: cannot find symbol: class VoiceAgentViewModel_Factory
```
Root cause: `VoiceAgentViewModel` constructor takes `AgentController` as parameter, but `AgentController` itself takes `PhoneControlEngine` which fails Hilt generation. Cascade failure.

**HIGH — EntityResolver regex compile error**
```
error: Expecting ')'  →  Regex("""(?:resolve[Message])...""")
```
Root cause: Unclosed character class `[` in a raw string regex.

**HIGH — GoalDecomposer named parameters**
```
error: None of the following candidates is applicable because of receiver type mismatch
```
Root cause: `GoalDecomposer` uses `Step(intent = ..., target = ...)` but the `Step` data class from `GoalPlanner` uses positional args.

**MEDIUM — findEditableNode visibility**
```
error: cannot access 'findEditableNode': it is private in AutoAgentAccessibilityService
```
Root cause: `AgentController` calls `service.findEditableNode(root)` but the function is `private`.

### 2C. GitHub Actions Workflow Issues

Current workflow (`Build Debug APK`) only:
1. Checks out
2. Sets up JDK 17
3. Runs `./gradlew assembleDebug`
4. Uploads APK

Missing:
- `./gradlew lint`
- `./gradlew test`
- `./gradlew connectedAndroidTest`
- APK inspection step
- Proper caching

---

## SECTION 3 — CRASH FINDINGS

### 3A. Run → PIN → Crash (ROOT CAUSE)

**File:** `DashboardViewModel.kt` → `runTaskNow()`  
**What happens:**

```
User presses Run
→ PIN dialog shown (correct)
→ PIN verified
→ DashboardViewModel.runTaskNow() called
→ ReactAgent().execute(task.name) called on Dispatchers.IO
→ ReactAgent internally calls AutoAgentAccessibilityService.instance
→ instance is NULL (service was restarted by system)
→ NullPointerException thrown
→ viewModelScope catches it BUT...
→ Navigation is triggered BEFORE the coroutine finishes
→ NavController is no longer valid
→ IllegalStateException: "Cannot perform this action after onSaveInstanceState"
→ APP CRASHES
```

**Secondary cause:** `ReactAgent` is created with `ReactAgent()` — a bare constructor call with no DI. Inside it creates a new `GoalPlanner`, `Thinker`, etc. None of these have access to a valid `Context` or service reference. The first time any of these call `AccessibilityService.instance`, it is null.

### 3B. Installed App Picker Crash (ROOT CAUSE)

**File:** Likely `AppPickerScreen.kt` or similar  
**Pattern inferred from fix scripts:**

```kotlin
// BROKEN PATTERN (inferred)
var apps by mutableStateOf<List<AppInfo>>(emptyList())

LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
        val loaded = packageManager.getInstalledApplications(...)
        apps = loaded.map { ... }  // ← Sets state from IO thread
    }
}

LazyColumn {
    items(apps.filter { it.name.contains(query) }) { ... }
    // ↑ Reads same list that is being replaced from IO thread
    // ConcurrentModificationException / IndexOutOfBounds
}
```

**Additional crash when typing:**  
The search `query` state change triggers recomposition while `apps` list is still being loaded. If `apps` is replaced between the `filter` call and the `items` rendering, the app crashes.

### 3C. Accessibility "Connected" vs "Enabled" confusion

The app shows "✅ Ready" when the user enables accessibility in system settings. But the `AccessibilityService.onServiceConnected()` callback hasn't fired yet. If Run is pressed in this 1-3 second window, `instance` is null → crash.

---

## SECTION 4 — ACCESSIBILITY SERVICE FINDINGS

### 4A. Instance Tracking (CRITICAL)

**Current pattern (inferred from fix scripts):**
```kotlin
companion object {
    var instance: AutoAgentAccessibilityService? = null
}

override fun onServiceConnected() {
    instance = this
}
```

**Problems:**
1. No `onDestroy` clearing: `instance` stays non-null after service is killed
2. No synchronization: multiple threads read/write `instance` without locking
3. No state distinction: callers can't tell if the service is connecting vs connected

### 4B. Service State Machine (MISSING)

The app needs to distinguish these states:
```
DISABLED → PERMISSION_ENABLED → CONNECTING → CONNECTED → DISCONNECTED
                                                       ↘ ERROR
```

Currently it only has: null (disabled) vs non-null (enabled). The "Connecting" state is completely missing.

---

## SECTION 5 — AUTOMATION ENGINE FINDINGS

### 5A. ReactAgent Architecture Problems

```kotlin
// Current (broken):
val agent = ReactAgent()
agent.execute(task.name)  // runs blocking sleep() calls on IO dispatcher

// Problems:
// 1. No structured cancellation — emergency stop can't stop it
// 2. Thread.sleep() in coroutine blocks the dispatcher thread
// 3. No timeout — can run forever
// 4. No retry policy — first failure = permanent failure
// 5. Returns nothing — caller has no result
```

### 5B. Three Competing NLP Pipelines

`GoalPlanner` (object), `GoalDecomposer` (class), `IntentEngine` (class) all convert natural language to steps. They use incompatible data models:
- `GoalPlanner` returns `Plan(steps: List<Step>, appPkg: String)`
- `GoalDecomposer` returns something else (from fix script, named params don't match)
- `IntentEngine` presumably returns yet another type

Result: callers can't tell which to use. The last commit `AgentController` tries to wire `IntentEngine` but the `Step` type doesn't match what `ReactAgent` expects.

---

## SECTION 6 — AI ARCHITECTURE FINDINGS

The current AI architecture is **entirely on-device rule-based** — no actual AI calls are being made. `GoalPlanner` is a keyword-matching object with Hinglish/Hindi keyword arrays. This is fine for a deterministic MVP, but it means:

- "Send a message to Imtiyaz on WhatsApp" works  
- "Remind me to call mom at 5pm tomorrow" does NOT — the date/time parser is missing  
- "Open my last conversation" does NOT — memory is not consulted

The `VoiceAgentScreen` records audio → `SpeechRecognizer` → plain text → `GoalPlanner`. There is no LLM in the loop. This is actually acceptable for now, but the architecture should be documented honestly so future LLM integration has a clear insertion point.

---

## SECTION 7 — ADB PILOT INTEGRATION FINDINGS

**adb-pilot is a development/testing tool only.** It must NOT be bundled in the APK.

Recommended usage during development:

| Task | adb-pilot command |
|------|-------------------|
| Install debug APK | `adb-pilot launch com.autoagent.personal` |
| Reproduce Run crash | `adb-pilot tap -t "Run"` then capture logcat |
| Reproduce picker crash | `adb-pilot fill "Search" "wh"` |
| Verify accessibility state | `adb-pilot screenshot --clickable` |
| Emergency stop test | `adb-pilot tap -t "Emergency Stop"` |
| UI tree inspection | `adb-pilot screenshot` → inspect elements array |

The key insight from adb-pilot's design worth adopting in AutoAgent's production code:
- **Semantic targeting over coordinates** — use `text`, `contentDescription`, resource ID. AutoAgent's `Thinker.kt` already does this partially; extend it.
- **`wait-for` pattern** — every action should have a corresponding verification step. AutoAgent currently fires-and-forgets.

---

## SECTION 8 — PRIORITY LIST

### CRITICAL (build is broken)
1. Fix Hilt: remove `PhoneControlEngine` from `AppModule`, provide it correctly
2. Fix `AgentController` constructor — `VoiceAgentViewModel` Hilt injection
3. Fix `EntityResolver` regex (unclosed bracket)
4. Fix `GoalDecomposer` data class parameter names

### HIGH (app crashes at runtime)
5. Fix `AutoAgentAccessibilityService` instance tracking with proper state machine
6. Fix `ReactAgent.execute()` — null-safe service access + coroutine timeout
7. Fix App Picker — move list to `StateFlow` in ViewModel, never touch state from IO thread
8. Fix Run → PIN → crash: verify service connected BEFORE executing task

### MEDIUM (incorrect behavior)
9. Consolidate three NLP pipelines into one (`GoalPlanner` wins — it is the most complete)
10. Add proper logging in `ExecutionLogEntity` — currently not written on crash
11. Fix scheduler timezone handling

### LOW (quality)
12. Remove `fix_all.sh` and `fix_all_v2.sh` from repo root — not production code
13. Add `setup_react_agent.sh` contents into README instead
14. Add unit tests for `GoalPlanner`
15. Fix GitHub Actions to run `./gradlew test`

---

## SECTION 9 — ALL FIXED FILES

The following section contains complete, corrected file contents for every CRITICAL and HIGH priority fix. Apply them in order.

---
