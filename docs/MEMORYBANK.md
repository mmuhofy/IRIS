# IRIS — Memory Bank

> Single source of truth for project context. Read at the start of every session. Update after every confirmed change. Do not contradict without Muhofy's explicit approval.

---

## 1. Project Identity

| Item | Value |
|---|---|
| App Name | IRIS |
| Package | `com.iris.asistant` |
| Owner | Muhammed (Muhofy) |
| Concept | JARVIS-style personal voice/AI assistant for Android |
| Naming note | Internally avoid "JARVIS" branding (Marvel/Disney trademark). "IRIS" = final name. |

---

## 2. Tech Stack (Verified June 2026)

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin | confirm version in `libs.versions.toml` |
| UI | Jetpack Compose | confirm BOM version |
| Architecture | MVVM + Clean Architecture | strict layering, see System Instructions |
| DI | Hilt | confirm version |
| DB | Room (2.x stable) | local-first, no cloud sync in MVP |
| Async | Coroutines + Flow | |
| Gradle Wrapper | 8.11.1 | confirmed working (Muhofy's other project) |
| Build | Gradle KTS + Version Catalog | |
| Min/Target/Compile SDK | 26 / 36 / 36 | Min: Android 8.0 (sufficient for Foreground Service, Adaptive Icon, Notification Channels). Target/Compile: Android 16 (latest) |
| Kotlin | 2.2.20 | known-working combo (Muhofy's other project) |
| Compose BOM | 2026.04.01 | known-working combo |
| Material 3 | 1.4.0 | via BOM |
| Hilt (Dagger) | 2.59.2 | confirmed by Muhofy (newer than known-working 2.57.1) |
| Room | 2.8.4 | confirmed both lists |
| Coroutines + Flow | 1.10.x | known-working combo |
| Fonts | Google Fonts (Compose) | via BOM |
| Navigation Compose | 2.9.0 | predictive back auto-enabled via manifest flag |

> Note: Newer versions exist (Compose BOM 2026.06.00 / Core 1.11.2 / Kotlin 2.4.0) but the above is a **proven working combination** from Muhofy's other project — use this baseline. If upgrading to Kotlin 2.4.0 later, KSP version must be updated to match (2.4.0-x.x.x), and Compose BOM bumped together as a set, not individually.

### AI / Voice Stack

| Function | Choice | Detail |
|---|---|---|
| LLM (primary) | Gemini | `gemini-3.5-flash` — **verify current model string before use**, Gemini model names change frequently |
| LLM (fallback) | Groq (Llama) | exact model TBD |
| STT | Groq Whisper | `whisper-large-v3`, `language=tr`, via `https://api.groq.com/openai/v1/audio/transcriptions` |
| TTS | Multi-provider (user-selectable) | Kokoro **dropped** — no Turkish support. See §3a. |
| Wake Word | openWakeWord (ONNX) | Picovoice rejected (requires corporate email for personal tier). MVP: prebuilt `hey_jarvis.onnx` model (no training needed) or manual mic button. Phase 2/3: train custom "Hey IRIS" via IT-BAER/hawake-wakeword pipeline (English only — openWakeWord doesn't support Turkish wake words yet). Android reference: hasanatlodhi/OpenwakewordforAndroid (ONNX Runtime). |
| Icons | Phosphor Icons | Regular weight default, Fill for active states |

### Backend
- **No backend in MVP.** Local-first (Room + DataStore).
- Supabase considered as optional future sync layer — not implemented, not prioritized.

---

## 3. Voice/Chat Pipeline

```
Wake Word (Porcupine "Hey IRIS")
    ↓
Audio recording (VAD: ~1.5s silence = stop)
    ↓
Groq Whisper-large-v3 (STT, tr)
    ↓
Gemini 3.5 Flash (+ tool calls if applicable)
    ↓
Kokoro TTS (audio output)
```

- Two interaction modes: **Voice Mode** (default home) and **Chat Mode** (text). Same backend/ViewModel.
- Multi-language: MVP = Turkish only (`language=tr` hardcoded). Phase 5 = auto-detect, remove lock, update system prompt to "respond in user's language."

---

## 3a. TTS — Multi-Provider (User-Selectable in Settings)

Kokoro-82M rejected — confirmed no Turkish voice support (only en/ja/zh/fr/es/hi/it/pt voice prefixes; voice = language, no separate language param).

Three providers, selectable in Settings → Voice:

| Provider | Default? | Notes |
|---|---|---|
| **Edge TTS** | ✅ Default | Microsoft, free, no API key. `tr-TR-AhmetNeural` / `tr-TR-EmelNeural`. Best out-of-box Turkish quality. |
| **XTTS v2** (Coqui) | Optional | Open source, voice cloning capable, 17 languages incl. Turkish. Heavier — likely needs external/self-hosted inference (HF Space or own server), not on-device. |
| **Gemini Live API (native audio)** | Optional | `gemini-2.5-flash-native-audio` family — audio-to-audio, very natural, but separate from main STT→LLM→TTS pipeline and has cost implications (not fully free). Selecting this may bypass Whisper+separate-TTS for that turn. |

- `data/remote/tts/TtsProvider.kt` — interface, with `EdgeTtsClient`, `XttsClient`, `GeminiLiveTtsClient` implementations.
- Settings UI: simple radio/dropdown — "Ses Motoru: Edge TTS / XTTS v2 / Gemini Live".
- All three implement same `suspend fun synthesize(text: String): AudioResult` contract so switching is transparent to the orchestrator.
- Voice/accent sub-selection (e.g., Ahmet vs Emel for Edge TTS) is a secondary setting shown only when that provider is active.

---

## 4. UI / Theme

### Design Language
- **Apple-style Modern Minimal**. Flat colors + subtle real shadows (NOT blur/glassmorphism). Gradients allowed for accents and Iris Core animation.
- Default: Dark mode. Light mode = Phase 5.
- Corner radius: 16–20dp. Animations: 200–300ms, no bounce/elastic/confetti.
- Font: system font (Inter/SF Pro equivalent), single family, varying weights.
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

Base dark colors (apply across all schemes):
```
Background = #18181B
Surface    = #27272A
Error      = #F87171
TextPrimary   = #FAFAFA
TextSecondary = #71717A
```

### Iris Core Animation (home screen central element)
- IDLE → slow pulse, ~40% opacity, gradient ring
- LISTENING → ring reacts to audio amplitude
- THINKING → ring rotates
- SPEAKING → ring wave-syncs with TTS output
- (Concept locked — no further redesign needed unless Muhofy requests)

### Home Screen Layout
```
Top bar: ☰ menu | ⚙ settings | 💬 chat-mode toggle
Center: Iris Core animation + status text
Bottom: 🎤 Mic toggle | 📺 Screen-control toggle | ⏹ Stop/interrupt
```
These three bottom controls are always visible.

### Navigation Transitions
- Defined as private functions in `IrisNavGraph.kt`.
- Timing: `Constants.NAV_ANIM_DURATION_MS = 300`, scale delta: `0.90f`.
- **Onboarding**: horizontal slide (1/4 screen offset) + fade (page-turn feel).
- **Main app** (Home ↔ Settings ↔ LocalModels): scale (0.90↔1.0) + fade (300ms).
- Scale pattern inspired by Peristyle: incoming screen scales up from 0.90 + fades in, outgoing scales down to 0.90 + fades out. Mirror on pop.
- Predictive back handled by Navigation Compose 2.9.0 automatically via `android:enableOnBackInvokedCallback="true"` in manifest. No manual `PredictiveBackHandler` registered.

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
Native API / AccessibilityService / EmbeddedShell
    ↓
ToolResult (Success | Error | PermissionRequired | Cancelled)
```

- Tools added one at a time, per Muhofy's spec (name, description, params, permission, logic). No auto-generated tools, no KSP code-gen (overkill for project size).
- Permissions requested **on first use** of each tool, with explanation — never bulk-requested at onboarding.

### Tool Categories (Phase 2)
- **Communication**: make_call, send_sms, read_notifications, open_whatsapp_chat
- **Productivity**: set_alarm, create_reminder, add_calendar_event, get_today_schedule, create_note
- **System**: open_app, set_volume, set_brightness, toggle_wifi, toggle_bluetooth, toggle_flashlight, get_battery_status
- **Information**: get_weather, web_search, get_news, calculate, get_current_time

---

## 6. Screen Reading & Control (Phase 3)

### Methods
- **Primary**: Accessibility Tree (`AccessibilityNodeInfo` traversal) — fast, free, text-based.
- **Fallback**: Screenshot + Gemini vision (MediaProjection) — for custom/Canvas UIs.

### Action Confirmation — "Action Preview Overlay"
- Before any screen action (click/type/scroll), show overlay: highlight target element + countdown bar + cancel button.
- Countdown duration depends on **Autonomy Level**:
  - **SAFE** (default): every action gets 1s preview + cancel; destructive actions require manual confirm (no countdown).
  - **BALANCED**: normal actions instant; destructive actions get preview+cancel.
  - **FULL_AUTO**: no previews at all; one-time warning dialog when first enabled.
  - **CUSTOM**: per-category configuration.
- "Destructive" detected via keyword match on element text: sil, gönder, onayla, satın al, ödeme, kabul.
- **No max-step limit** on read→decide→act loops (explicit decision — Muhofy does not want a step cap).
- Sensitive app blacklist (banking apps, password managers): screen control disabled entirely regardless of autonomy level.
- Global "Stop/interrupt" control always available (bottom bar ⏹).

---

## 7. Embedded Shell / "Power Mode" (Phase 4)

### Purpose — Two Use Cases (confirmed by Muhofy)

1. **Tool Fallback** — When no structured JarvisTool exists for a task, Gemini writes and executes a raw shell command to accomplish it (e.g., batch rename files, compress a folder, run a one-off task).
2. **Direct Shell Use** — Full general-purpose terminal capability: run Python servers, execute scripts, manage processes, anything a Linux shell can do.

### Decision Summary
- **No separate Termux app required.** A minimal Linux environment (proot + Termux bootstrap) is downloaded and embedded inside IRIS itself, activated via Settings → "Power Mode".
- AI writes **raw shell commands directly** (e.g., `python3 server.py`, `cp a.txt b.txt`, `pip install flask`), executed via `EmbeddedShell.execute(command)`.
- Scope: **unrestricted** — any shell-executable task, not limited to file operations.
- Bootstrap (~50-100MB) downloaded only when user enables Power Mode (not bundled in APK).
- Storage access via bind-mount (`/storage:/storage`) inside proot; `~/storage/shared` etc. symlinks set up during install.
- Shell session is **persistent** (long-running process) — not spawned per command. Supports stateful workflows (e.g., activate venv, then run script).
- stdout/stderr streamed back to IRIS in real time — both displayed in terminal UI and optionally summarized by Gemini.

### Terminal UI (Settings → Power Mode → Terminal)
- Full terminal screen: scrollable output, text input field, send button.
- Used for both AI-driven commands and manual user input.
- Running processes shown with a stop button (SIGTERM/SIGKILL).

### Security Model — User-Configurable, Default UNRESTRICTED
| Level | Behavior |
|---|---|
| **UNRESTRICTED (default — Muhofy's explicit choice)** | AI runs any command immediately, no blacklist, no confirmation. One-time warning dialog on first Power Mode activation only. |
| CONFIRM_EACH | Every command shown in ActionPreviewOverlay (command text + 1s countdown + cancel) before execution. |
| RESTRICTED | Regex blacklist for dangerous patterns (`rm -rf /`, `dd if=`, `chmod -R 777 /`, fork bombs) + path whitelist to `/storage/emulated/0/*`. |

### Open Verification Items (flagged — verify before implementation)
- Exact Termux bootstrap release URLs/asset names per ABI — verify against Termux GitHub releases before implementation.
- Whether proot binary ships inside bootstrap or requires custom NDK build.
- `MANAGE_EXTERNAL_STORAGE` permission requirement for scoped storage on target SDK.

### Implementation notes (BootstrapInstaller.kt, IrisShellSession.kt)
- **targetSdk=28** is critical — `untrusted_app` (≤28) allows `execute` on app data files; `untrusted_app_29` (29+) blocks it. This is why we fork+execvp() directly instead of using linker64 load.
- **Bootstrap downloaded at runtime** from GitHub releases (`bootstrap-aarch64.zip`), SHA-256 verified, cached in `filesDir/bootstrap/`. Not embedded.
- **ELF patching** after extraction:
  - DT_RUNPATH rewritten from `/data/data/com.termux/files/usr/lib` → `/data/data/com.iris.assistant/u/lib` (symlink to actual lib dir). Uses same-length string overwrite in-place.
  - Hardcoded `/data/data/com.termux/files/usr` strings (SYSCONFDIR etc.) replaced with `/data/data/com.iris.assistant/p` via `patchTermuxDataPaths()`. Same-length replacement + symlink `p` → `files/usr`.
  - **Bug (2026-06-26, fix in a78d975+):** `off += 24` used in dynamic entry iteration — `Elf64_Dyn` entries are 16 bytes, not 24. This caused misparsing that randomly overwrote DT_NEEDED library names with the RUNPATH path, producing `libiris-exec-hook.so` dependency on bash. Fixed to `off += 16`.
  - **Bug (2026-06-26):** `addAndroidNote()` wrote a note with type `0x40000000` (ANDROID_NOTES_TYPES_START, not a valid note type) with descsz=0. Did nothing useful; removed.
- **No LD_PRELOAD needed** — targetSdk=28 allows direct execve. Previous LD_PRELOAD hook (`libiris-exec-hook.so`) removed.
- **Symlinks after install:**
  - `/data/data/com.iris.assistant/u/lib` → `files/usr/lib` (RUNPATH resolution)
  - `/data/data/com.iris.assistant/p` → `files/usr` (SYSCONFDIR etc.)

---

## 8. Permissions & Privacy

- Permissions requested **on first use**, never bulk at onboarding (except mic, requested during onboarding for core function).
- Audio recordings: temporary only, deleted immediately after STT — never persisted.
- Conversation history: local Room only, user can clear via Settings.
- No sensitive data (transcripts, tool args with personal info) in persistent logs.
- API keys: GitHub Secrets + `local.properties` (never committed); `local.properties.example` documents required keys.

---

## 9. Onboarding Flow

1. Welcome + name confirmation ("Muhofy")
2. Microphone permission + explanation
3. Wake word ("Hey IRIS") test
4. Quick demo command
5. Battery optimization whitelist request
→ Lands on Home (Voice Mode). No tutorial overlays/tooltips after this.

---

## 10. Personality / System Prompt Notes

- IRIS addresses user as "Muhofy".
- Tone: zeki, sakin, hafif esprili, profesyonel.
- Emotion-awareness: handled via **text-based sentiment** (Gemini's natural understanding) — no separate audio-emotion model. System prompt instructs tone adaptation (üzgün → destekleyici, sinirli → sakin/çözüm odaklı, etc.) without explicitly stating "I sense you're sad."
- Respond in user's language (supports future multi-language without hardcoding).

---

## 11. Phase Roadmap (summary — full detail in todo.md)

- **Phase 1 (MVP)**: Wake word, STT, Gemini chat, TTS, Iris Core UI, Chat mode, local history, onboarding, theming.
- **Phase 2**: Tool system + permission-on-first-use + background service.
- **Phase 3**: Screen reading/control + Action Preview Overlay + Autonomy Levels.
- **Phase 4**: Embedded Shell (Power Mode) — tool fallback + direct shell; macros, cross-app workflows, floating bubble, default-assistant (VoiceInteractionService) + power-button trigger.
- **Phase 5**: Multi-language, proactive suggestions, notification filtering, light theme, sentiment-based TTS tuning.

---

## 12. Misc System Behaviors

### Stop / Interrupt
- Triggered by: bottom bar ⏹ button, or voice "Dur IRIS" / "Yeter".
- Effects: `Job.cancel()` on current AI/tool coroutine, `tts.stop()` on audio playback, `requestStop()` flag on screen-control loop (finishes current step, then halts).
- Applies globally — all long-running operations must be cancellable coroutines per System Instructions.

### App Icon & Splash Screen
- Icon: simple "iris" (eye) geometric shape, gradient from active color scheme, Adaptive Icon (foreground/background layers).
- Splash: native Android 12+ SplashScreen API (`androidx.core.splashscreen`), simple fade-in, ~500ms-1s, no complex animation.

### Widget
- Phase 5. Single "tap to talk" widget via Glance API — opens app and starts listening.

### Notification Quick Actions
- Phase 2, alongside reminder/alarm tools. Use `NotificationCompat.Action` + `PendingIntent` (e.g., "Ertele 5dk", "Tamamlandı").

### Background Access
- Wake-word listening runs as a Foreground Service with a persistent low-priority "IRIS aktif" notification (required by Android 8+, cannot be swiped away while service runs).
- User can disable background listening in Settings (app then only listens while foreground).

### Crash Reporting
- MVP: none (Logcat + manual testing only — simplicity priority).
- Future (if needed): ACRA (self-hosted/email reports) preferred over Firebase Crashlytics for privacy reasons (avoids third-party data collection).

---

## 13. Decisions Explicitly NOT Taken (avoid re-litigating)

- ❌ Native Android SpeechRecognizer for STT (replaced by Groq Whisper — quality complaint).
- ❌ Gemini native-audio-to-audio Live API (cost/complexity vs. ücretsiz hedefi — staying with Whisper+Kokoro pipeline).
- ❌ Glassmorphism / heavy futuristic neon UI (rejected — Apple-style modern instead).
- ❌ Fully automatic AI-written-and-compiled Kotlin tools (security/feasibility — rejected; raw shell via Power Mode is the chosen "flexible execution" path instead).
- ❌ Max-step cap on screen-control loops (explicitly rejected by Muhofy).
- ❌ Separate Termux app + termux-api dependency (replaced by embedded proot environment).
- ❌ Shell scope limited to file operations only (rejected — full general-purpose shell; see §7).