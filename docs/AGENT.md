# System Instructions — IRIS Dev Agent

---

## ANTI-HALLUCINATION PROTOCOL (HIGHEST PRIORITY)

These rules override everything else:

- **NEVER invent API names, method signatures, class names, or library features.** If you are not certain a method exists in the exact version being used, say so explicitly.
- **NEVER assume a dependency version is compatible.** Always reference the exact version from the project's `gradle/libs.versions.toml` or `build.gradle.kts`.
- **If you don't know something, say "I don't know" or "I need to verify this."** Do not fill gaps with plausible-sounding fabrications.
- **When referencing Jetpack Compose or any Jetpack API:** always explicitly state the target version. If uncertain about a class or method existing in that exact version, flag it.
- **When referencing external APIs (Gemini, Groq Whisper, Kokoro TTS, Picovoice Porcupine):** always explicitly state the API version/endpoint used. If uncertain, flag it and ask Muhofy to confirm via official docs.
- **Code that has not been tested must be labeled:** add a comment `// UNTESTED — verify before use` on any non-trivial logic block that cannot be fully verified.
- **Do not silently rename or refactor existing code** unless explicitly asked. Muhofy's existing code is canonical.

---

## CODING STANDARDS

- All code comments in **English**
- No magic numbers — use named constants in `util/Constants.kt`
- No hidden side effects
- Explicit error handling — no silent failures
- Use `sealed class` / `sealed interface` for UI state, events, and tool results
- Prefer immutable data (`val` over `var`, immutable collections)
- Use Kotlin `data class` for all model/entity types
- All suspend functions must be called from appropriate coroutine scopes
- Never access the database, network, or Accessibility/Termux APIs directly from a ViewModel — goes through Use Cases

### Layer Responsibilities

```
ui/          — Jetpack Compose screens, components, ViewModels. No direct data access.
domain/      — Use cases, business logic, repository interfaces, JarvisTool interfaces. No Android dependencies (except domain/tools where unavoidable — flag these explicitly).
data/        — Repository implementations, Room DAOs, local data sources, remote API clients (Gemini, Whisper, Kokoro, Porcupine).
service/     — Foreground services, AccessibilityService, VoiceInteractionService, overlay windows.
di/          — Hilt modules only. No logic here.
util/        — Constants, extension functions, shared helpers.
```

### Data Flow (strict, no shortcuts)

```
Compose Screen
      ↓ UI events
ViewModel (ui/)
      ↓ calls
Use Case (domain/)
      ↓ calls
Repository Interface (domain/)
      ↓ implemented by
Repository Impl (data/)
      ↓
Room DAO / Remote API Client
      ↓
SQLite (Room) / Network (Gemini, Whisper, Kokoro, Groq)
```

### Tool System Flow (specific to IRIS)

```
Gemini function_call response
      ↓
ToolRegistry.execute(name, args)
      ↓
JarvisTool implementation (domain/tools interface, data/tools or service/tools impl)
      ↓ (if screen action)
ActionPreviewOverlay (confirmation/countdown per Autonomy Level)
      ↓
AccessibilityService / Termux Intent / Native Android API
      ↓
ToolResult (Success | Error | PermissionRequired | Cancelled)
```

---

## FILE & ARTIFACT RULES

- Always provide files as artifacts — never write them inline as text
- One artifact per file
- Always include the full file content — never truncate
- If updating an existing file, use the artifact update mechanism
- Artifact title must match the actual filename (e.g., `ToolRegistry.kt`)

### Artifact Path Requirements

- Always include the full project path for every artifact.
- **Never refer to a file by name alone (e.g., "ToolRegistry.kt") in any explanation, instruction, or list — always pair it with its full path** (e.g., `app/src/main/java/com/iris/assistant/domain/tools/ToolRegistry.kt`).
- Format:

  app/src/main/java/com/iris/assistant/domain/tools/JarvisTool.kt

- Never provide only the filename.
- When multiple files are modified, list every affected file path separately.

---

## GIT COMMIT RULES

### Build Environment Rules

- Local builds are NOT considered authoritative.
- **Builds are ALWAYS run via GitHub Actions — never locally.** Do not suggest or instruct local `./gradlew` builds as a verification step; assume Muhofy has no local build environment.
- The project is built and validated through GitHub Actions.
- API keys (Gemini, Groq, HuggingFace/Kokoro, Picovoice) are stored in **GitHub Secrets**, never committed.
- A `local.properties.example` file documents required keys without values.
- Every code change MUST include a proposed commit message.
- Commit messages are required because GitHub Actions builds are triggered from commits.
- Never finish an implementation response without providing a commit message.

### When to output a commit message
- Every implementation, fix, refactor, or configuration change must include a commit message.
- Documentation-only discussions may omit commit messages.
- **Never output a commit message speculatively.**
- **Never output a commit message for documentation-only responses.**

### Commit message format

```
"<type>(<scope>): <short description>"
```

**Types:**

| Type | When to use |
|------|-------------|
| `feat` | New feature added |
| `fix` | Bug fix confirmed working |
| `refactor` | Code restructured, no behavior change |
| `perf` | Performance improvement |
| `style` | Formatting, linting, no logic change |
| `docs` | Documentation only |
| `test` | Tests added or updated |
| `chore` | Tooling, config, build scripts |

**Rules:**
- Imperative mood (`add`, `fix`, `remove`)
- All lowercase, no period, max 72 chars
- Scope = module (e.g., `ui`, `domain`, `data`, `service`, `di`, `util`, `tools`)

**Examples:**
```
feat(tools): add set_alarm jarvis tool with permission check
feat(service): implement accessibility service for screen reading
feat(ui): add iris core pulse animation to home screen
fix(data): fix groq whisper multipart request content type
chore(deps): add porcupine and room dependencies
```

---

## WEB RESEARCH PROTOCOL

- If a web fetch or search returns a "failed to fetch" or empty result:
  1. **Do not hallucinate the content**
  2. Provide the exact URL to Muhofy
  3. Ask Muhofy to paste the relevant content
  4. Only proceed once Muhofy has provided the actual content
- For external AI APIs (Gemini, Groq, Kokoro, Porcupine), if behavior/parameters are uncertain, search official docs before writing integration code — do not guess request/response shapes.

---

## TARGET STACK (VERIFIED — June 2026)

| Component | Technology | Version / Detail |
|-----------|-----------|-------------------|
| App Name | `IRIS` | — |
| Package | `com.iris.assistant` | — |
| Language | Kotlin | confirm in `libs.versions.toml` |
| UI Framework | Jetpack Compose | confirm BOM in `libs.versions.toml` |
| Material | Material 3 | via BOM |
| Min SDK | TBD | confirm before first build |
| Target / Compile SDK | TBD | confirm before first build |
| Architecture | MVVM + Clean Architecture | — |
| DI | Hilt | confirm in `libs.versions.toml` |
| Navigation | Navigation Compose | via BOM |
| Local DB | Room | confirm version (2.x stable, NOT 3.0 alpha) |
| Async | Kotlin Coroutines + Flow | confirm version |
| LLM (primary) | Google Gemini API | `gemini-3.5-flash` |
| LLM (fallback) | Groq (Llama) | model TBD, confirm via Groq docs |
| STT | Groq Whisper | `whisper-large-v3`, `language=tr` param |
| TTS | Kokoro TTS | via HuggingFace Inference API |
| Wake Word | Picovoice Porcupine | custom "Hey IRIS" keyword via Picovoice Console |
| Icons | Phosphor Icons | weight: Regular (default), Fill (active states) |
| Build System | Gradle KTS + Version Catalog | — |
| Annotation Processor | KSP | confirm version matches Kotlin |

> ⚠️ Always confirm versions against `gradle/libs.versions.toml` before referencing any API.
> ⚠️ Room 3.0 is alpha as of this writing — use latest stable 2.x until 3.0 is stable.
> ⚠️ Gemini model strings change frequently — always verify current model name against official docs (`ai.google.dev/gemini-api/docs/models`) before assuming `gemini-3.5-flash` is still current/available.

---

## ARCHITECTURE RULES

- `ui/` screens never import from `data/` directly — domain layer is the boundary
- `domain/` has zero Android imports — pure Kotlin only, **except** `domain/tools/JarvisTool.kt` interface which may reference `android.os.Bundle`/`JsonObject`-equivalent types if unavoidable — flag any such exception explicitly in code comments
- `data/` implements interfaces defined in `domain/`
- `service/` (AccessibilityService, VoiceInteractionService, ForegroundService, Overlay) calls into `domain/` use cases — never contains business logic itself
- `di/` binds `data/`/`service/` implementations to `domain/` interfaces via Hilt modules
- ViewModels expose `StateFlow<UiState>` — never expose mutable state directly
- UI collects state with `collectAsStateWithLifecycle()`

---

## UI & DESIGN RULES

- Design language: **Apple-style Modern Minimal** — flat colors, subtle shadows (no blur/glassmorphism), gradients allowed for accents and the Iris Core animation
- Default theme: **Dark**, color scheme selectable in Settings (Lavender, Sunset, Ocean, Forest, Rose, Monochrome — see Memory Bank for exact hex values)
- Typography: system font (Inter / SF Pro equivalent)
- Icons: Phosphor Icons, Regular weight default, Fill for active/selected states, tinted with theme primary color
- Corner radius: 16–20dp (squircle feel)
- Animations: 200–300ms for transitions, no bounce/elastic/confetti
- Iris Core animation (home screen central element):
  - IDLE → slow pulse, ~40% opacity, gradient ring
  - LISTENING → ring reacts to audio amplitude
  - THINKING → ring rotates
  - SPEAKING → ring wave-syncs with TTS output
- Bottom-of-screen quick controls on home screen: Mic toggle, Screen-control toggle, Stop/interrupt — always visible
- Two interaction modes: **Voice Mode** (default home screen) and **Chat Mode** (text-based, same backend)

### Feature Defaults

**ON by default:**
- Wake word listening ("Hey IRIS")
- Autonomy Level: SAFE (1s preview + cancel on screen actions, manual confirm on destructive actions)
- Dark theme (Lavender color scheme)
- Conversation history (local Room only)

**OFF by default (user enables in Settings):**
- Termux file-operation tools
- Screen reading/control (Accessibility Service)
- Autonomy Level BALANCED / FULL_AUTO / CUSTOM
- Multi-language auto-detection (MVP = Turkish only)

---

## ONBOARDING RULES

- First launch only, sequential screens, no skip on screen 1
- Screen 1: Welcome + name confirmation ("Muhofy")
- Screen 2: Microphone permission request, with explanation
- Screen 3: Wake word test ("Hey IRIS" detection confirmation)
- Screen 4: Quick demo command (e.g., "What time is it?")
- Screen 5: Battery optimization whitelist request (for background wake-word service)
- After onboarding: land directly on Home (Voice Mode) screen
- Permissions for tools (calls, SMS, contacts, accessibility, Termux) are requested **on first use of that tool**, not during onboarding

---

## SECURITY & PRIVACY RULES

- No sensitive data logged (transcripts, tool arguments containing personal info never logged in plaintext to persistent logs)
- Audio recordings are temporary — deleted immediately after STT processing, never persisted
- Conversation history stored locally in Room only — no cloud sync in MVP
- Destructive actions (delete, send, confirm, pay-related button text) require **manual confirmation regardless of Autonomy Level** unless user explicitly sets FULL_AUTO and has acknowledged a one-time warning
- Screen control is disabled by default for a blacklist of sensitive app categories (banking, password managers) — see Memory Bank for list
- Autonomy Level changes (especially to FULL_AUTO) require explicit confirmation dialog explaining implications
- API keys never hardcoded — injected via `BuildConfig` from GitHub Secrets / `local.properties`

---

## TOOL SYSTEM RULES

- Every tool implements the `JarvisTool` interface (`domain/tools/JarvisTool.kt`): `name`, `description`, `parameters`, `requiredPermission`, `suspend fun execute(args): ToolResult`
- `ToolResult` is a `sealed class`: `Success`, `Error`, `PermissionRequired`, `Cancelled`
- Screen-interaction tools (click, type, scroll) MUST go through `ActionPreviewOverlay` per current Autonomy Level setting before execution
- Termux tools are restricted to file operations only (move, copy, delete, read, write, list, search, rename) — no arbitrary shell command execution exposed to the AI
- New tools added one at a time, per Muhofy's spec (name, description, params, permission, logic) — no speculative/auto-generated tools

---

## PHASE PLAN

### Phase 1 — MVP ("Çalışan IRIS")
- Wake word ("Hey IRIS") via Porcupine
- STT via Groq Whisper-large-v3 (tr)
- Gemini 3.5 Flash chat (no tools yet, or minimal: get_time, calculate)
- TTS via Kokoro
- Home screen: Iris Core animation (Voice Mode), basic Chat Mode
- Local conversation history (Room)
- Onboarding flow
- Theme system (color scheme + dark mode)

### Phase 2 — Tool-Enabled IRIS
- Communication tools (call, SMS, notifications)
- Productivity tools (alarm, reminder, calendar)
- System tools (volume, brightness, wifi, bluetooth, app launch)
- Information tools (weather, web search, news)
- Permission-on-first-use flow
- Foreground service for background wake-word listening

### Phase 3 — Screen Intelligence
- AccessibilityService: read_screen
- Screen control tools: click, type, scroll, navigate
- ActionPreviewOverlay (countdown + cancel)
- Autonomy Level system (SAFE / BALANCED / FULL_AUTO / CUSTOM)
- Sensitive app blacklist

### Phase 4 — Power Features
- Termux integration (file operations only)
- Macro/workflow recording & replay
- Cross-app workflows
- Floating bubble assistant
- VoiceInteractionService (default assistant / power button trigger)
- AssistStructure-based screen context

### Phase 5 — Polish
- Multi-language auto-detection
- Proactive suggestions / habit learning
- Smart notification filtering
- Light theme support

---

## PROJECT FILE ACCESS RULES

### Raw File Protocol

- The agent does NOT assume project files exist locally.
- Before analyzing, modifying, or referencing project code, request the GitHub raw file URL from Muhofy.
- Always fetch and read the provided raw file before making code changes.

### Raw Link Freshness Rule

- Never reuse previously provided raw file contents.
- If the same file is needed again in a later task, request the raw URL again.
- Treat previously fetched file contents as potentially outdated.
- Always re-fetch from a newly provided raw URL before performing analysis or modifications.

### Missing File Rule

- If a required file has not been provided as a raw URL:
  - Stop implementation.
  - Request the raw URL.
  - Do not guess file contents.
  - Do not reconstruct missing code from memory.

---

## MEMORY BANK

The Memory Bank is the single source of truth for project context across sessions.

- **Always read `memory-bank.md` at the start of every session**
- **Always update `memory-bank.md` after every confirmed change**
- Never contradict Memory Bank content without explicit approval from Muhofy
- If Memory Bank is missing or incomplete, ask Muhofy before proceeding