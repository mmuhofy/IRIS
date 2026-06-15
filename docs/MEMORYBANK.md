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
| LLM (primary) | Gemini | `gemini-3.5-flash` — verify at implementation time. Function calling integrated. |
| LLM (fallback) | Groq (Llama) | `llama-3.3-70b-versatile` — confirm via Groq docs |
| STT | Groq Whisper | `whisper-large-v3`, `language=tr` |
| TTS | See §3a | Multi-provider, user-selectable |
| Wake Word | openWakeWord (ONNX) | MVP: prebuilt `hey_jarvis.onnx`. Phase 2: custom "Hey IRIS" |
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
Gemini function_call
    ↓
ToolRegistry.execute(name, args)
    ↓
JarvisTool implementation
    ↓ (if screen action)
ActionPreviewOverlay (per Autonomy Level)
    ↓
Native API / AccessibilityService / Embedded Shell
    ↓
ToolResult (Success | Error | PermissionRequired | Cancelled)
```

- Tools added one at a time, per Muhofy's spec.
- Permissions requested on first use of each tool.

### Tool Categories (Phase 2)
- **Communication**: make_call, send_sms, read_notifications, open_whatsapp_chat
- **Productivity**: set_alarm, create_reminder, add_calendar_event, get_today_schedule, create_note
- **System**: open_app, set_volume, set_brightness, toggle_wifi, toggle_bluetooth, toggle_flashlight, get_battery_status
- **Information**: get_weather, web_search, get_news, calculate, get_current_time

---

## 6. Screen Reading & Control (Phase 3)

- **Primary**: Accessibility Tree (`AccessibilityNodeInfo` traversal)
- **Fallback**: Screenshot + Gemini vision (MediaProjection)

### Action Confirmation — "Action Preview Overlay"
- SAFE (default): every action gets 1s preview + cancel
- BALANCED: normal actions instant; destructive get preview+cancel
- FULL_AUTO: no previews; one-time warning dialog
- CUSTOM: per-category config
- Destructive keywords: sil, gönder, onayla, satın al, ödeme, kabul
- No max-step limit on read→decide→act loops
- Sensitive app blacklist: screen control disabled for banking/password managers

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

- **Phase 1 (MVP)** ✅ COMPLETE: Wake word (manual mic), STT, Gemini chat, Android TTS, Iris Core UI, Chat mode, local history (Room), DataStore preferences, onboarding, theming, splash screen.
- **Phase 2**: Tool system (framework ✅, function-calling integration ✅, permission-on-first-use ✅) + TTS upgrade (XTTS v2 or Gemini Live) + background service + openWakeWord integration.
- **Phase 3**: Screen reading/control + Action Preview Overlay + Autonomy Levels.
- **Phase 4**: Embedded Shell (Power Mode), macros, cross-app workflows, floating bubble, default-assistant.
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
- Wake-word listening: Foreground Service + persistent "IRIS aktif" notification (Phase 2)
- User can disable in Settings

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
│       │   └── GroqLlmRepository.kt        (stub — Phase 2)
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