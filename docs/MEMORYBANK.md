# IRIS — Memory Bank

> Single source of truth for project context. Read at the start of every session. Update after every confirmed change. Do not contradict without Muhofy's explicit approval.

---

## 1. Project Identity

| Item | Value |
|---|---|
| App Name | IRIS |
| Package | `com.iris.assistant` |
| Owner | Muhammed (Muhofy) |
| Concept | JARVIS-style personal voice/AI assistant for Android |
| Naming note | Internally avoid "JARVIS" branding (Marvel/Disney trademark). "IRIS" = final name. |

---

## 2. Tech Stack (Verified June 2026)

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin | 2.2.20 |
| UI | Jetpack Compose | BOM 2026.04.01 |
| Architecture | MVVM + Clean Architecture | strict layering |
| DI | Hilt | **2.57.1** (2.59.2 incompatible with AGP 8.x) |
| DB | Room | 2.8.4 |
| Async | Coroutines + Flow | 1.10.1 |
| Gradle Wrapper | **8.13** (AGP 8.11.1 requires minimum 8.13) | |
| AGP | 8.11.1 | |
| Build | Gradle KTS + Version Catalog | |
| Min/Target/Compile SDK | 26 / 36 / 36 | |
| Kotlin | 2.2.20 | |
| Compose BOM | 2026.04.01 | |
| Material 3 | 1.4.0 | via BOM |
| Room | 2.8.4 | |
| Coroutines | 1.10.1 | |
| KSP | 2.2.20-2.0.3 | must match Kotlin 2.2.20 |
| AppCompat | 1.7.0 | required for Theme.MaterialComponents + SplashScreen |
| MaterialComponents | 1.12.0 | required for Theme.MaterialComponents.DayNight.NoActionBar |

### AI / Voice Stack

| Function | Choice | Detail |
|---|---|---|
| LLM (primary) | Gemini | `gemini-3.5-flash` — verify at implementation time. Function calling via `functionDeclarations`. |
| LLM (fallback) | Groq (Llama) | `llama-3.3-70b-versatile`. JSON `{"tool":"name","args":{}}` in text (NOT native function calling — see TODO). |
| LLM (local) | llama-kotlin-android 0.1.5 | Llama-3.2-1B GGUF. JSON tool calling in text. Qwen2.5 models NOT supported (missing arch). |
| STT | Groq Whisper | `whisper-large-v3`, `language=tr` |
| TTS | See §3a | Multi-provider, user-selectable |
| Wake Word | openWakeWord (ONNX) | Two models loaded simultaneously: prebuilt `hey_jarvis.onnx` + custom-trained `hey_iris.onnx` via Colab. |
| Icons | Phosphor Icons | Regular weight default, Fill for active states |

### Backend
- No backend in MVP. Local-first (Room + DataStore).

---

## 3. Voice/Chat Pipeline

```
Wake Word (openWakeWord "Hey IRIS")
    ↓
Audio recording (VAD: ~1.5s silence = stop)
    ↓
Groq Whisper-large-v3 (STT, tr)
    ↓
Gemini 3.5 Flash (+ tool calls Phase 2+)
    ↓
TTS (provider-selectable, see §3a)
```

- Two interaction modes: **Voice Mode** (default home) and **Chat Mode** (text). Same backend/ViewModel.
- Multi-language: MVP = Turkish only (`language=tr` hardcoded). Phase 5 = auto-detect.

---

## 3a. TTS — Multi-Provider (User-Selectable in Settings)

**EdgeTTS DROPPED from roadmap** — Microsoft WebSocket reverse-engineering protocol, unstable, no official API. Too fragile for production.

| Provider | Default? | Status | Notes |
|---|---|---|---|
| **Android built-in TTS** | ✅ Default (MVP) | ✅ Implemented | `TextToSpeech` API, `Locale("tr")`, offline, zero dependency |
| **XTTS v2** (Coqui) | Optional | Phase 2 | Open source, voice cloning, Turkish support. Needs external inference (HF Space or self-hosted). |
| **Gemini Live API (native audio)** | Optional | Phase 2 | `gemini-2.5-flash-native-audio` family. Very natural but separate pipeline + cost implications. |

**Priority for Phase 2 TTS upgrade:** XTTS v2 first, then Gemini Live if XTTS hosting is impractical.

- `data/remote/tts/TtsProvider.kt` — interface: `suspend fun speak(text, onProgress, onDone)` + `stop()` + `release()`
- `data/remote/tts/AndroidTtsClient.kt` — current implementation (MVP)
- Settings UI: radio/dropdown for provider selection (Phase 2)

---

## 4. UI / Theme

### Design Language
- **Apple-style Modern Minimal**. Flat colors + subtle real shadows (NOT blur/glassmorphism).
- Default: Dark mode. Light mode = Phase 5.
- Corner radius: 16–20dp. Animations: 200–300ms.
- Font: system font (Inter/SF Pro equivalent).
- Icons: Phosphor (Regular default, Fill for active).

### Color Schemes (user-selectable in Settings)

| Scheme | Primary | Gradient End | Secondary |
|---|---|---|---|
| Lavender (default) | `#A78BFA` | `#818CF8` | `#34D399` |
| Sunset | `#FF6B6B` | `#FFB627` | `#FFD93D` |
| Ocean | `#06B6D4` | `#3B82F6` | `#34D399` |
| Forest | `#34D399` | `#10B981` | `#FCD34D` |
| Rose | `#FB7185` | `#A78BFA` | `#FCD34D` |
| Monochrome | `#E4E4E7` | `#A1A1AA` | `#34D399` |

Base dark colors:
```
Background = #18181B
Surface    = #27272A
Error      = #F87171
TextPrimary   = #FAFAFA
TextSecondary = #71717A
```

### Iris Core Animation
- IDLE → slow pulse, ~40% opacity, gradient ring
- LISTENING → ring reacts to audio amplitude
- THINKING → ring rotates
- SPEAKING → ring wave-syncs with TTS output
- Concept locked.

### Home Screen Layout
```
Top bar: ☰ menu | IRIS title | 💬 chat-mode toggle | ⚙ settings
Center: Iris Core animation + status text
Bottom: 🎤 Mic toggle | ⏹ Stop/interrupt | 📺 Screen-control toggle
```

---

## 5. Tool System

### Architecture
```
Gemini / Groq / Local LLM
    ↓
ToolRegistry.execute(name, args)
    ↓
JarvisTool implementation
    ↓ (if screen action)
ActionPreviewOverlay (spotlight highlight + countdown + cancel per Autonomy Level)
    ↓
Native API / AccessibilityService
    ↓
ToolResult (Success | Error | PermissionRequired | Cancelled)
```

- Three LLM providers: Gemini (native `functionDeclarations`), Groq (JSON `{"tool":"name","args":{}}` in text), Local (same JSON format).
- Tools added one at a time, per Muhofy's spec.
- Permissions requested on first use of each tool.
- Screen tools: ClickTool, ScrollTool, TypeTool pass coordinates to `ScreenActionGate.awaitApproval()` for spotlight highlight. NavigateTool still needs coordination update.

### Tool Categories (Phase 2)
- **Communication**: make_call, send_sms, read_notifications, open_whatsapp_chat
- **Productivity**: set_alarm, create_reminder, add_calendar_event, get_today_schedule, create_note (via kosekull)
- **System**: open_app, set_volume, set_brightness, toggle_wifi, toggle_bluetooth, toggle_flashlight, get_battery_status
- **Information**: get_weather, web_search, get_news, calculate, get_current_time

### Screen Tools (Phase 3)
- **click(x, y, description)**: taps node, highlights via ActionPreviewOverlay
- **type(text)**: types into focused node
- **scroll(x, y, direction)**: swipes at coordinates
- **navigate_back(action)**: go back/recents/home
- **read_screen**: returns accessibility tree as JSON

---

## 5a. VoiceInteractionService (Power Button Assistant)

### Architecture
```
Power button long-press (system)
    ↓
IrisVoiceInteractionService (VoiceInteractionService)
    ↓ (onLaunchVoiceAssistFromKeyguard if locked)
IrisVoiceInteractionSessionService → IrisVoiceInteractionSession
    ↓ (onShow — unlocked)
startAssistantActivity(Intent → AssistantActivity)
    ↓
Translucent overlay launches → record → STT → LLM → TTS → close
```

### Key Details
- Power button long-press ONLY (no AssistStructure — AccessibilityService handles screen reading)
- `onLaunchVoiceAssistFromKeyguard()` — lock screen activation, opens AssistantActivity directly
- `onShow()` — unlocked activation via `IrisVoiceInteractionSession`, calls `startAssistantActivity(Intent → AssistantActivity)`
- AssistantActivity is a translucent overlay (`Theme.IRIS.Translucent`, `#CC1C1C1E` background) that floats over the current app
- AssistantViewModel runs a one-shot voice pipeline: record → STT → LLM → TTS → `isDone=true` → auto-close after 1.5s
- No conversation history, wake word, or screen control in the assistant-only flow
- `IrisVoiceInteractionService` is `@AndroidEntryPoint` (Hilt)
- Onboarding Step 5: "Set IRIS as default assistant" via `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` (API 31+)
- Session service `android:exported="true"`
- `BIND_VOICE_INTERACTION` uses-permission REMOVED from manifest (signature-level, not grantable)

### Files
- `service/voice/IrisVoiceInteractionService.kt` — launches AssistantActivity on keyguard trigger
- `service/voice/IrisVoiceInteractionSessionService.kt`
- `service/voice/IrisVoiceInteractionSession.kt` — `onShow` opens AssistantActivity
- `service/voice/VoiceInteractionEntryPoint.kt`
- `ui/assistant/AssistantActivity.kt` — translucent Compose overlay with IrisCoreAnimation, status, transcript, response, close button
- `ui/assistant/AssistantViewModel.kt` — one-shot voice pipeline (record → STT → LLM → TTS → close)
- `res/values/themes.xml` — added `Theme.IRIS.Translucent`
- `res/xml/voice_interaction_service.xml`
- Manifest: both services + AssistantActivity + IrisRecognitionService declared

### Debug Suffix Consideration
- Debug build has `applicationIdSuffix = ".debug"` → package = `com.iris.assistant.debug`
- `voice_interaction_service.xml` uses full class name (NOT `.prefix`) for `sessionService` to avoid `ComponentName.createRelative` prepending the debug package
- `ComponentName.createRelative("com.iris.assistant.debug", "com.iris.assistant.service.voice.IrisVoiceInteractionSessionService")` correctly resolves

### Root Cause & Fix

**Root cause:** `VoiceInteractionServiceInfo.java` requires `recognitionService` to be non-null:
```java
if (mRecognitionService == null) {
    mParseError = "No recognitionService specified";  // ← IRIS was filtered here
    return;
}
```
`voice_interaction_service.xml` was missing `android:recognitionService`, so `VoiceInteractionServiceInfo` set a parse error, and the system filtered IRIS out of the assistant picker (`VoiceInputHelper.buildUi()` checks `info.getParseError() != null` and skips).

**Fix (3 files changed + 2 files created):**
1. **Created `service/voice/IrisRecognitionService.kt`** — full `RecognitionService` implementation: records audio via `AudioRecord`, sends to Groq Whisper, returns results via callback
2. **Created `res/xml/recognition_service.xml`** — minimal recognition service metadata
3. **Updated `res/xml/voice_interaction_service.xml`** — added `android:recognitionService="com.iris.assistant.service.voice.IrisRecognitionService"`
4. **Updated `AndroidManifest.xml`** — declared `IrisRecognitionService` with `BIND_VOICE_INTERACTION` permission + `android.speech.RecognitionService` intent filter + `android.speech` metadata

**Secondary changes (also applied):**
- Removed `<uses-permission android:name="BIND_VOICE_INTERACTION" />` (signature-level, not grantable, may interfere)
- Changed session service `android:exported="false"` → `"true"` (some query paths check exported flag)

---

## 6. Screen Reading & Control (Phase 3)

- **Primary**: Accessibility Tree (`AccessibilityNodeInfo` traversal)
- **Fallback**: Screenshot + Gemini vision (MediaProjection) — NOT IMPLEMENTED
- **Service**: `IrisAccessibilityService` — singleton via `companion object instance`, tools access through it
- **Node matching**: text/contentDescription first, coordinate fallback

### Action Confirmation — "Action Preview Overlay" ✅
- `ActionPreviewOverlay` — `WindowManager` overlay with `TYPE_APPLICATION_OVERLAY`
- Full-screen dark overlay (`#CC000000`)
- **Spotlight**: `RadialGradient` at click coordinates with animation
- **Countdown**: center text (3...2...1...), auto-confirms at 0
- **Cancel button**: top-right "✕" immediately cancels
- **Ripple**: circular reveal animation on confirm
- **SAFE** (default): 3s preview + cancel button
- **BALANCED**: 1s preview
- **FULL_AUTO**: no preview
- Uses `suspendCancellableCoroutine` for structured concurrency
- `ScreenActionGate` creates overlay instance, calls `.show()`, returns `ToolResult`
- **Remaining**: navigate tool improved label (no coords — system-level action)

---

## 7. Embedded Shell / "Power Mode" (Phase 4)

- No separate Termux app. Minimal Linux (proot + Termux bootstrap) embedded inside IRIS.
- AI writes raw shell commands → `EmbeddedShell.execute(command)`
- Bootstrap (~50-100MB) downloaded only when user enables Power Mode.
- Default security: UNRESTRICTED (one-time warning on first activation).

---

## 8. Permissions & Privacy

- Permissions requested on first use (except mic — onboarding).
- Permission flow: tool returns `ToolResult.PermissionRequired` → GeminiRepository throws `IrisException.PermissionRequiredException` → ViewModel catches, shows dialog → `ActivityResultContracts.RequestPermission` → on grant, retry the message.
- Permission rationale shown in AlertDialog before system permission dialog.
- Audio: temporary only, deleted after STT.
- Conversation history: local Room only.
- API keys: GitHub Secrets + `local.properties` (never committed).

---

## 9. Onboarding Flow

1. Welcome + name confirmation ("Muhofy")
2. Microphone permission + explanation
3. Wake word test ("Hey IRIS")
4. Quick demo command
5. Battery optimization whitelist
→ Lands on Home (Voice Mode). `onboardingCompleted = true` saved to DataStore.

---

## 10. Personality / System Prompt Notes

- IRIS addresses user as "Muhofy".
- Tone: zeki, sakin, hafif esprili, profesyonel.
- Emotion-awareness via text-based sentiment (Gemini's natural understanding).
- Respond in user's language (supports future multi-language).

---

## 11. Phase Roadmap

- **Phase 1 (MVP)** ✅ COMPLETE: Wake word, STT, Gemini/Groq chat, Kokoro TTS + Android TTS, Iris Core UI, Chat mode, local history (Room), onboarding, theming, splash screen.
- **Phase 2 (Tool-enabled)** ✅ CLOSE TO DONE: Tool system (framework ✅, 15+ tools implemented ✅, Gemini/Groq/local LLM function calling ✅, permission-on-first-use ✅, Settings redesign ✅). Remaining: local LLM performance tuning, weather tool debug.
- **Phase 3 (Screen Intelligence)** 🔶 IN PROGRESS: ActionPreviewOverlay ✅, ScreenActionGate ✅, ClickTool/ScrollTool/TypeTool ✅, NavigateTool label fix ✅, accessibility perf fix ✅ (bg thread + debounce + node recycling), VoiceInteractionService ✅ (power button trigger). Pending: verify IRIS appears in default assistant picker (fix applied: added mandatory `recognitionService` to `voice_interaction_service.xml` — push + test needed), AndroidSpeechRecognizer fallback for STT.
- **Phase 4**: Embedded Shell (Power Mode), macros, cross-app workflows, floating bubble.
- **Phase 5**: Multi-language, proactive suggestions, notification filtering, light theme.

---

## 12. Misc System Behaviors

### Stop / Interrupt
- Bottom bar ⏹ or voice "Dur IRIS" / "Yeter"
- `Job.cancel()` + `ttsProvider.stop()` + screen-loop `requestStop()`

### App Icon & Splash Screen
- Icon: "iris" geometric shape, gradient per active color scheme, Adaptive Icon
- Splash: `androidx.core.splashscreen` API, background `#18181B`, fade-in

### Background Access
- Wake-word listening: `WakeWordManager` singleton (in-app only — inference only when app visible)
  - `startListening()` / `stopListening()` called by `HomeViewModel.onScreenVisible/Hidden()`
  - No foreground service, no notification, no persistent background wake word
- WakeWordService: simplified to bare minimum — only used by onboarding "Hey IRIS" test step. No foreground/notification.
- STOP_KEYWORDS: `{"dur", "yeter", "kes", "tamam", "stop", "tamammı"}` — stops TTS mid-speech when detected via STT pipeline

---

## 13. Decisions Explicitly NOT Taken

- ❌ EdgeTTS — WebSocket reverse-engineering, unstable, dropped permanently
- ❌ Native Android SpeechRecognizer (replaced by Groq Whisper)
- ❌ Glassmorphism / heavy neon UI
- ❌ Fully automatic AI-written Kotlin tools
- ❌ Max-step cap on screen-control loops
- ❌ Separate Termux app
- ❌ Hilt 2.59.2 (incompatible with AGP 8.x — use 2.57.1)
- ❌ Gradle wrapper < 8.13 (AGP 8.11.1 requires 8.13 minimum)
- ❌ Qwen2.5 GGUF models (unsupported in llama-kotlin-android 0.1.5 — use Llama-3.2)
- ❌ Native Groq function calling (`tools` API param) — Llama 3.3 70B generates malformed XML. Switched to JSON-based calling in `buildGroqSystemPrompt()`.
- ❌ Wake word foreground service — replaced by `WakeWordManager` singleton (in-app only), saves battery + avoids persistent notification
- ❌ `<uses-permission android:name="BIND_VOICE_INTERACTION" />` — signature-level permission, not grantable to third-party apps via uses-permission; may cause system to filter app from assistant picker
- ❌ `android:exported="false"` on session service — `VoiceInteractionServiceInfo` validates via `PackageManager.queryIntentServices()` which skips non-exported components
- ❌ Omitting `recognitionService` from `voice_interaction_service.xml` — `VoiceInteractionServiceInfo` sets `mParseError = "No recognitionService specified"` which filters the app out of the assistant picker

---

## 14. Phase 1 — Implemented Files

```
app/src/main/java/com/iris/assistant/
├── IrisApplication.kt
├── ui/
│   ├── MainActivity.kt
│   ├── IrisApp.kt                          (AppViewModel + root composable)
│   ├── navigation/
│   │   ├── NavRoute.kt
│   │   └── IrisNavGraph.kt
│   ├── home/
│   │   ├── IrisCoreState.kt
│   │   ├── IrisCoreAnimation.kt
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── chat/
│   │   ├── ChatScreen.kt
│   │   ├── ChatViewModel.kt
│   │   └── MessageBubble.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   ├── onboarding/
│   │   ├── OnboardingWelcomeScreen.kt
│   │   ├── OnboardingScreens.kt            (Mic, WakeWord, Demo, Battery)
│   │   └── OnboardingViewModel.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   └── components/
│       ├── IrisButton.kt
│       └── IrisCard.kt
├── domain/
│   ├── model/
│   │   ├── ChatMessage.kt
│   │   ├── IrisException.kt
│   │   └── SystemPrompt.kt
│   ├── repository/
│   │   ├── SttRepository.kt
│   │   ├── LlmRepository.kt
│   │   └── ConversationRepository.kt
│   └── usecase/
│       ├── TranscribeAudioUseCase.kt
│       └── SendMessageUseCase.kt
├── data/
│   ├── audio/
│   │   └── AudioRecorder.kt
│   ├── local/
│   │   ├── db/
│   │   │   ├── IrisDatabase.kt
│   │   │   ├── ConversationRepositoryImpl.kt
│   │   │   ├── dao/
│   │   │   │   └── MessageDao.kt
│   │   │   └── entity/
│   │   │       └── MessageEntity.kt
│   │   └── datastore/
│   │       ├── UserPreferences.kt
│   │       └── PreferencesRepository.kt
│   └── remote/
│       ├── gemini/
│       │   └── GeminiRepository.kt
│       ├── groq/
│       │   ├── WhisperRepository.kt
│   │   └── GroqLlmRepository.kt        (JSON tool calling — no native tools param)
│       └── tts/
│           ├── TtsProvider.kt
│           └── AndroidTtsClient.kt
├── di/
│   ├── NetworkModule.kt
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   ├── TtsModule.kt
│   └── LlmQualifiers.kt
└── util/
    └── Constants.kt

app/src/main/res/
├── values/
│   ├── themes.xml
│   ├── colors.xml
│   └── strings.xml
└── AndroidManifest.xml

gradle/
├── libs.versions.toml
└── wrapper/
    └── gradle-wrapper.properties           (8.13)

.github/workflows/ci.yml
local.properties.example
```

## 15. Phase 2–3 — Additional Implemented Files

```
app/src/main/java/com/iris/assistant/
├── domain/
│   ├── model/SystemPrompt.kt               (English, tool-calling focused)
│   ├── repository/LlmRepository.kt          (interface)
│   └── tools/
│       ├── JarvisTool.kt                    (interface)
│       ├── ToolRegistry.kt                  (Gemini + OpenAI tool payloads)
│       └── ToolResult.kt                    (sealed class: Success/Error/PermissionRequired/Cancelled)
├── data/
│   ├── remote/
│   │   ├── gemini/GeminiRepository.kt       (native functionDeclarations)
│   │   ├── groq/GroqLlmRepository.kt        (JSON tool calling in text)
│   │   ├── local/
│   │   │   ├── LocalLlmRepository.kt        (llama-kotlin-android)
│   │   │   ├── LocalModelManifest.kt        (4 bundled GGUF models)
│   │   │   └── ModelDownloader.kt           (app-scoped singleton, progress)
│   │   └── router/LlmProviderRouter.kt
│   ├── tools/
│   │   ├── ClickTool.kt                     (findTargetRect + coords to overlay)
│   │   ├── ScrollTool.kt                    (scroll coords)
│   │   ├── TypeTool.kt                      (focused node bounds)
│   │   ├── NavigateTool.kt                  (no coords yet)
│   │   ├── ReadScreenTool.kt                (accessibility tree dump)
│   │   └── screen/ScreenInteractionRepository.kt
│   └── local/datastore/ (preferences for autonomy level, model selection)
├── service/
│   ├── accessibility/IrisAccessibilityService.kt
│   └── overlay/
│       ├── ActionPreviewOverlay.kt          (WindowManager, spotlight, countdown, ripple)
│       └── ScreenActionGate.kt              (awaitApproval via overlay)
├── di/
│   ├── ToolsModule.kt                       (@IntoSet multibinding)
│   └── ...
├── ui/
│   ├── assistant/
│   │   ├── AssistantActivity.kt             (translucent overlay for power-button trigger)
│   │   └── AssistantViewModel.kt            (one-shot voice pipeline)
│   ├── settings/LocalModelScreen.kt         (download + select GGUF models)
│   └── components/ (SettingsGroup, SettingsIcon, SettingsRowWithContent, etc.)
├── util/
│   ├── Constants.kt                         (growth_llm_model, provider names)
│   └── DownloadState.kt                     (Idle/Connecting/Downloading/Ready/Error)
│   └── ToolCallParser.kt                    (brace-depth parser for JSON tool calls)
├── service/
│   ├── voice/
│   │   ├── IrisVoiceInteractionService.kt   (VoiceInteractionService, power button trigger)
│   │   ├── IrisVoiceInteractionSessionService.kt
│   │   ├── IrisVoiceInteractionSession.kt   (onShow → AssistantActivity)
│   │   └── VoiceInteractionEntryPoint.kt    (Hilt entry point)
│   └── wakeword/
│       └── WakeWordManager.kt               (singleton, in-app only, no service/notification)
└── ...
```