# AutoAgent — Fix Migration Guide
> Apply these changes IN ORDER. Do not skip steps.

---

## STEP 0 — Before you start

```bash
git checkout -b fix/critical-crashes
```

Do every fix on this branch. Merge to main only after CI passes.

---

## STEP 1 — Delete conflicting files (CRITICAL)

These files caused compile failures in builds 81–87 because they use
incompatible types and duplicate the role of GoalPlanner.kt:

```bash
# Delete from the repo — these are replaced by the fixed files below
rm app/src/main/java/com/autoagent/personal/agent/GoalDecomposer.kt
rm app/src/main/java/com/autoagent/personal/agent/IntentEngine.kt
```

Also remove the fix scripts from the repo root — they are not production code:
```bash
rm fix_all.sh
rm fix_all_v2.sh
rm setup_react_agent.sh
```

---

## STEP 2 — Copy fixed files to correct locations

```
fixes/AutoAgentAccessibilityService.kt
  → app/src/main/java/com/autoagent/personal/service/AutoAgentAccessibilityService.kt

fixes/AppModule.kt
  → app/src/main/java/com/autoagent/personal/di/AppModule.kt

fixes/AgentController.kt
  → app/src/main/java/com/autoagent/personal/agent/AgentController.kt

fixes/GoalPlanner.kt
  → app/src/main/java/com/autoagent/personal/agent/GoalPlanner.kt

fixes/EntityResolver.kt
  → app/src/main/java/com/autoagent/personal/agent/EntityResolver.kt

fixes/DashboardViewModel.kt
  → app/src/main/java/com/autoagent/personal/presentation/dashboard/DashboardViewModel.kt

fixes/VoiceAgentViewModel.kt
  → app/src/main/java/com/autoagent/personal/presentation/voice/VoiceAgentViewModel.kt

fixes/AppPickerViewModel.kt
  → app/src/main/java/com/autoagent/personal/presentation/apppicker/AppPickerViewModel.kt

fixes/AppPickerScreen.kt
  → app/src/main/java/com/autoagent/personal/presentation/apppicker/AppPickerScreen.kt

fixes/res/xml/accessibility_service_config.xml
  → app/src/main/res/xml/accessibility_service_config.xml

fixes/.github/workflows/android.yml
  → .github/workflows/android.yml

fixes/test/GoalPlannerTest.kt
  → app/src/test/java/com/autoagent/personal/agent/GoalPlannerTest.kt
```

---

## STEP 3 — Update AndroidManifest.xml

Verify your manifest has ALL of the following. Patch anything missing:

```xml
<manifest ...>

    <!-- Required for automation -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Required for installed app listing -->
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <application ...>

        <!-- Accessibility service — verify this block exactly -->
        <service
            android:name=".service.AutoAgentAccessibilityService"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>
</manifest>
```

Common mistakes to check:
- `android:exported="true"` must be present on the service
- `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"` must be exact
- The `meta-data` name must be `"android.accessibilityservice"` (no dot at end)
- `@xml/accessibility_service_config` must match the file you placed in `res/xml/`

---

## STEP 4 — Add missing string resources

If not already in `res/values/strings.xml`:

```xml
<string name="accessibility_service_description">
    AutoAgent needs Accessibility permission to automate apps on your behalf.
    It reads the screen to find buttons and text fields, and performs taps
    and text entry to complete your tasks.
</string>
<string name="accessibility_service_summary">Automate app tasks</string>
```

---

## STEP 5 — Verify data model compatibility

The fixed files reference these types. Confirm they exist in your project:

```
com.autoagent.personal.data.domain.model.RunStatus  (enum: SUCCESS, FAILED, CANCELLED)
com.autoagent.personal.data.domain.model.TriggerType (enum: MANUAL, SCHEDULED, ...)
com.autoagent.personal.data.repository.TaskRepository
  └── fun getAllTasks(): List<TaskEntity>
  └── fun getTask(id: Long): TaskEntity?
  └── fun updateTaskLastRun(id: Long, timestamp: Long, status: RunStatus)
com.autoagent.personal.data.util.GsonHelper
  └── fun entityToTask(entity: TaskEntity): Task
com.autoagent.personal.memory.MemoryEngine
  └── fun saveLastCommand(text: String)
```

If any of these don't exist yet, create minimal stubs first, then build.

---

## STEP 6 — Build locally

```bash
# Clean first — KSP caches can mask fixes
./gradlew clean

# Run unit tests (JVM only, no device needed)
./gradlew test

# If tests pass, assemble
./gradlew assembleDebug

# Install on connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: zero KSP errors, tests green, APK installs.

---

## STEP 7 — Manual verification checklist

After installing on a device:

### A. Accessibility Service
- [ ] Open app → go to Settings/Permissions screen
- [ ] Tap "Enable Accessibility" → opens system accessibility settings
- [ ] Enable AutoAgent → return to app
- [ ] Status should show "Connected" (not just "Enabled")
- [ ] Status should NOT show "Ready" until `onServiceConnected` has fired

### B. Task Builder + App Picker
- [ ] Press "+" (FAB) → Task Builder opens WITHOUT PIN
- [ ] Tap "Choose Installed App" → App Picker opens
- [ ] Apps load (may take 2–3 seconds for icons)
- [ ] Type in search bar → list filters without crash
- [ ] Type rapidly (stress test) → no crash
- [ ] Select an app → returns to Task Builder with app name filled

### C. Run → PIN → Execute (the main crash scenario)
- [ ] Create a task: "YouTube pe Arijit suno"
- [ ] Press Run on the dashboard
- [ ] PIN dialog appears
- [ ] Enter correct PIN → press Verify
- [ ] App stays open (does NOT crash)
- [ ] Accessibility Service launches YouTube
- [ ] Status shows "▶️ task start hua..." then result

### D. Emergency Stop
- [ ] Start a running task
- [ ] Press Emergency Stop
- [ ] Task halts within one step
- [ ] App stays open

### E. Service disconnect during task
- [ ] Start a long task
- [ ] Disable accessibility mid-execution in system settings
- [ ] App should show "Service disconnected" error — NOT crash

---

## STEP 8 — Push and verify CI

```bash
git add .
git commit -m "fix: critical crashes — Hilt, AccessibilityService, AppPicker, ReactAgent"
git push origin fix/critical-crashes
```

Wait for GitHub Actions "Build & Test AutoAgent" to complete.

Expected outcome:
- Lint step: passes or warnings only (continue-on-error)
- Unit tests: ✅ GoalPlannerTest and EntityResolverTest green
- APK: uploaded as artifact

---

## What was NOT fixed (known remaining limitations)

| Issue | Status | Why |
|-------|--------|-----|
| Scheduled tasks / WorkManager timezone | Not fixed | Separate issue, lower priority |
| PIN flow for Task Builder (should not require PIN) | Not fixed — verify your NavGraph | Depends on TaskBuilderScreen code not fetched |
| Biometric fallback for PIN | Not implemented | Future work |
| LLM integration (actual AI, not keyword matching) | Not implemented | Intentional — keyword matching is reliable MVP |
| RecoveryEngine / RiskEngine wiring | Not wired | These classes are stubs — do not wire until core is stable |
| VerificationEngine | Not wired | Same reason |
| `ExperienceRecorder` duplicate of `ExecutionLogEntity` | Not removed | Needs careful audit of all callers |
| Tests for DashboardViewModel | Not added | Requires Hilt test setup (HiltAndroidTest) |
| UI (Compose) tests for AppPickerScreen | Not added | Requires emulator |

---

## Recommended next steps (priority order)

1. **Wire the PIN flow correctly** — PIN should only appear at task confirmation, not Task Builder entry
2. **Add `WorkManager` timezone fix** for scheduler
3. **Add `HiltAndroidTest` for DashboardViewModel**
4. **Remove `ExperienceRecorder`** — replace all usages with `ExecutionLogEntity`
5. **Remove `RecoveryEngine`, `RiskEngine`, `VerificationEngine`** stubs until the core is solid
6. **Add Compose UI tests** for AppPickerScreen search flow
7. **Consider real LLM integration** via Anthropic API for date/time parsing and contact resolution
