# IRIS — Project Todo

> Status legend: `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked/needs decision

---

## Phase 0 — Project Setup

- [ ] Create Android Studio project: package `com.iris.assistant`
- [ ] Set Min SDK 26, Target/Compile SDK 36
- [ ] Set up `gradle/libs.versions.toml` (Kotlin, Compose BOM, Hilt, Room, Coroutines, KSP — confirm exact versions)
- [ ] Confirm Min SDK / Target SDK / Compile SDK ✅ (26 / 36 / 36)
- [ ] Set up Clean Architecture folder skeleton: `ui/`, `domain/`, `data/`, `service/`, `di/`, `util/`
- [ ] Create `util/Constants.kt` (placeholder)
- [ ] Set up GitHub repository
- [ ] Configure GitHub Secrets: `GEMINI_API_KEY`, `GROQ_API_KEY`, `HUGGINGFACE_API_KEY`, `PICOVOICE_ACCESS_KEY`
- [ ] Create `local.properties.example` documenting required keys (no values)
- [ ] Set up GitHub Actions CI (build + lint on push)
- [ ] Create `memory-bank.md` and keep updated after every confirmed change

---

## Phase 1 — MVP ("Çalışan IRIS")

### Theme & Design System
- [ ] Define `Color.kt` for all 6 color schemes (Lavender, Sunset, Ocean, Forest, Rose, Monochrome)
- [ ] Define `Type.kt` (typography, system font)
- [ ] Define `Theme.kt` (dark mode default, scheme selection wiring)
- [ ] Integrate Phosphor Icons dependency
- [ ] Build base components: `IrisCard`, `IrisButton` (primary/secondary/destructive), gradient utilities

### Onboarding
- [ ] Screen 1: Welcome + name confirmation ("Muhofy")
- [ ] Screen 2: Microphone permission request + explanation
- [ ] Screen 3: Wake word ("Hey IRIS") test/confirmation
- [ ] Screen 4: Quick demo command flow
- [ ] Screen 5: Battery optimization whitelist request
- [ ] Onboarding completion → navigate to Home (Voice Mode)

### Voice Pipeline
- [ ] Integrate openWakeWord (ONNX Runtime) — start with prebuilt `hey_jarvis.onnx` (or manual mic-button trigger for true MVP)
- [ ] `service/WakeWordService.kt` — foreground service, listens for wake word
- [ ] Audio recording on wake word trigger (`MediaRecorder` or `AudioRecord`)
- [ ] Voice Activity Detection (VAD) — auto-stop recording after silence (~1.5s)
- [ ] `data/remote/WhisperApiClient.kt` — Groq Whisper-large-v3 STT (`language=tr`)
- [ ] `data/remote/GeminiApiClient.kt` — Gemini 3.5 Flash chat (verify model string before use)
- [ ] `data/remote/GroqLlmClient.kt` — fallback LLM (verify Groq model name)
- [ ] `data/remote/tts/TtsProvider.kt` — common interface (`synthesize(text): AudioResult`)
- [ ] `EdgeTtsClient` — default provider, `tr-TR-AhmetNeural`/`tr-TR-EmelNeural`
- [ ] `XttsClient` — Coqui XTTS v2 (verify hosting approach: HF Space vs self-hosted)
- [ ] `GeminiLiveTtsClient` — native audio option (verify model name + cost before enabling by default)
- [ ] Settings: TTS provider selector + voice sub-selection (per provider)
- [ ] Audio playback for TTS output

### Home Screen (Voice Mode)
- [ ] Iris Core animation: Canvas-based gradient ring (IDLE/LISTENING/THINKING/SPEAKING states)
- [ ] Status text display
- [ ] Bottom quick controls: Mic toggle, Screen-control toggle, Stop/interrupt
- [ ] Top bar: menu, settings, chat-mode icons

### Chat Mode
- [ ] Chat screen UI (message list, input field)
- [ ] Chat ↔ Voice mode switching
- [ ] Shared ViewModel/backend between modes

### Local Storage
- [ ] Room schema: conversation history (messages, timestamps, role)
- [ ] DAO + Repository for conversation history
- [ ] DataStore: user preferences (name, theme, autonomy level, etc.)
- [ ] Settings screen: "Clear history" action

### Core LLM Loop
- [ ] `domain/usecase/SendMessageUseCase.kt` — orchestrates STT → Gemini → TTS
- [ ] System prompt v1 (IRIS personality, Turkish, emotion-awareness per text)
- [ ] Error handling: no internet, API limit, STT failure (per System Instructions error rules)
- [ ] Groq fallback trigger logic (on Gemini rate limit/error)

---

## Phase 2 — Tool-Enabled IRIS

### Tool Framework
- [ ] `domain/tools/JarvisTool.kt` interface
- [ ] `domain/tools/ToolResult.kt` sealed class (Success, Error, PermissionRequired, Cancelled)
- [ ] `domain/tools/ToolRegistry.kt` — registration, lookup, Gemini function-declaration formatting
- [ ] Gemini function-calling integration in `GeminiApiClient`
- [ ] Permission-on-first-use flow (request when tool first triggered, with explanation dialog)

### Communication Tools
- [ ] `make_call(contact)`
- [ ] `send_sms(contact, message)`
- [ ] `read_notifications()` (NotificationListenerService)
- [ ] `open_whatsapp_chat(contact, message)`

### Productivity Tools
- [ ] `set_alarm(time, label)`
- [ ] `create_reminder(text, datetime)`
- [ ] `add_calendar_event(title, date, time, location?)`
- [ ] `get_today_schedule()`
- [ ] `create_note(text)`

### System Tools
- [ ] `open_app(appName)`
- [ ] `set_volume(level, type)`
- [ ] `set_brightness(level)`
- [ ] `toggle_wifi(state)`
- [ ] `toggle_bluetooth(state)`
- [ ] `toggle_flashlight(state)`
- [ ] `get_battery_status()`

### Information Tools
- [ ] `get_weather(city)`
- [ ] `web_search(query)`
- [ ] `get_news(topic)`
- [ ] `calculate(expression)`
- [ ] `get_current_time()`

### Background Service
- [ ] Foreground service for persistent wake-word listening
- [ ] Persistent notification ("IRIS aktif")
- [ ] Battery optimization handling

---

## Phase 3 — Screen Intelligence

- [ ] `service/IrisAccessibilityService.kt` — accessibility service setup + manifest config
- [ ] `read_screen()` — accessibility tree parsing → text representation
- [ ] Screen control actions: `click_element`, `type_text`, `scroll`, `go_back`, `go_home`
- [ ] `take_screenshot()` (MediaProjection) — vision fallback for non-tree-readable UIs
- [ ] `service/ActionPreviewOverlay.kt` — WindowManager overlay (highlight + countdown + cancel)
- [ ] Autonomy Level system: SAFE / BALANCED / FULL_AUTO / CUSTOM (settings + enforcement logic)
- [ ] Destructive action keyword detection (sil, gönder, onayla, satın al, ödeme, kabul)
- [ ] Sensitive app blacklist (banking, password managers) — screen control disabled
- [ ] Multi-step read→decide→act loop (no max-step limit, per Muhofy's decision)
- [ ] "Stop/interrupt" wired into screen-control loop

---

## Phase 4 — Power Features

### Embedded Shell ("Power Mode")
- [ ] Bootstrap download flow (ABI detection, progress UI) — verify Termux bootstrap release URLs/assets first
- [ ] `BootstrapInstaller` — extract, symlink, permission setup
- [ ] proot binary sourcing (from bootstrap or NDK build) — verify before implementation
- [ ] `EmbeddedShell.execute(command)` — proot-based command execution
- [ ] `execute_shell_command` tool (raw command from AI)
- [ ] `ShellSecuritySettings` — UNRESTRICTED / CONFIRM_EACH / RESTRICTED, default UNRESTRICTED
- [ ] Settings UI: Power Mode toggle + security level selector + one-time warning dialog
- [ ] Investigate `MANAGE_EXTERNAL_STORAGE` requirement for scoped storage access

### Other Power Features
- [ ] Macro/workflow recording & replay system
- [ ] Cross-app workflow execution
- [ ] Floating bubble assistant (overlay icon, quick actions)
- [ ] `VoiceInteractionService` + `VoiceInteractionSessionService` (default assistant, power-button trigger)
- [ ] `onHandleAssist` — AssistStructure-based screen context capture
- [ ] Settings: "Set as Default Assistant" → deep link to system settings

---

## Phase 5 — Polish

- [ ] Multi-language auto-detection (remove `language=tr` lock from Whisper, update system prompt)
- [ ] Proactive suggestions / habit learning (pattern tracking, opt-in)
- [ ] Smart notification filtering/prioritization
- [ ] Light theme support
- [ ] TTS tone/speed adjustment based on detected text sentiment

---

## Phase 1 Additions — Misc

- [ ] App icon: adaptive icon, "iris" geometric shape, gradient per active color scheme
- [ ] Splash screen via `androidx.core.splashscreen` (simple fade-in)
- [ ] `IrisOrchestrator.stop()` — wires Job.cancel(), TTS.stop(), screen-loop requestStop()
- [ ] Voice "Dur IRIS" / "Yeter" → triggers stop()
- [ ] Settings: "Background listening" toggle (foreground service on/off)
- [ ] Foreground service persistent "IRIS aktif" notification

## Phase 2 Additions — Misc

- [ ] Notification quick actions for reminders/alarms (`NotificationCompat.Action` + `PendingIntent`)
- [ ] Train custom "Hey IRIS" wake word model (IT-BAER/hawake-wakeword pipeline, English-based)

## Phase 5 Additions — Misc

- [ ] Home screen widget ("tap to talk") via Glance API
- [ ] (If needed) ACRA crash reporting integration — privacy-preserving alternative to Firebase

## Open Questions / Needs Verification

- [!] Confirm exact Gemini model string (`gemini-3.5-flash` vs alternatives) at implementation time
- [!] Confirm Groq fallback model name
- [!] Confirm XTTS v2 hosting approach (HF Space API vs self-hosted server)
- [!] Confirm Gemini Live native-audio model name + cost/free-tier limits before enabling as TTS option
- [!] Verify Termux bootstrap asset URLs/format for embedded shell
- [!] Verify proot inclusion in bootstrap vs need for custom NDK build
- [!] Confirm Min/Target/Compile SDK versions
- [!] Confirm exact dependency versions (Compose BOM ~2026.05.00, Hilt ~2.57.x, Room ~2.8.x, Kotlin ~2.2.x) against Android Studio new-project template at setup time
✅ Confirmed baseline: Kotlin 2.2.20, Compose BOM 2026.04.01, Hilt 2.59.2, Room 2.8.4, KSP 2.2.20-2.0.3