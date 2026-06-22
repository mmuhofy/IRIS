# IRIS — Yapılacaklar & Notlar

---

## Phase 1-2: Tamamlanan ✅

- [x] Onboarding flow
- [x] Theme sistemi (Lavender, Sunset, Ocean, Forest, Rose, Monochrome)
- [x] Home screen: Iris Core animation + quick controls
- [x] Chat Mode (Voice Mode + Chat Mode toggle)
- [x] Wake word "Hey IRIS" (Picovoice Porcupine)
- [x] STT (Groq Whisper-large-v3, tr)
- [x] TTS (Kokoro HF API)
- [x] Gemini LLM + function calling
- [x] Conversation history (Room)
- [x] Tool sistemi: get_time, calculate, send_sms, make_call, set_alarm, set_reminder, get_weather, web_search, take_note, control_volume, control_brightness, toggle_wifi, toggle_bluetooth, launch_app, open_website, read_notifications, get_news
- [x] Settings sayfası (Still design: grup kartları, icon circles, section header)
- [x] Provider/Model/Voice selector
- [x] Color scheme picker
- [x] System prompt (İngilizce, tool-calling odaklı)

---

## Phase 3: Tamamlanan ✅ (2 madde sonraya bırakıldı)

- [x] AutonomyLevel (SAFE / BALANCED / FULL_AUTO / CUSTOM)
- [x] ScreenInteractionRepository
- [x] IrisAccessibilityService (servis + manifest)
- [x] Screen tools: click, type, scroll, navigate_back, read_screen
- [x] ActionPreviewOverlay (spotlight, countdown, ripple, cancel)
- [x] ScreenActionGate (overlay üzerinden approval)
- [x] Autonomy Level picker UI (Settings)
- [x] NavigateTool — label iyileştirildi, koordinatsız (sistem aksiyonu)
- [x] Accessibility performans fix — background thread + 150ms debounce + node recycling

**Sonraya bırakıldı (Phase 5 polish):**
- [ ] Accessibility service aktivasyon rehberi — kullanıcıya adım adım açıklama ekranı
- [ ] Sensitive app blacklist — bankacılık/şifre uygulamaları için kara liste

---

## Phase 4: Devam Ediyor 🔄

### 4a. Embedded Shell / Power Mode — ANA HEDEF

**2 kullanım amacı (Muhofy tarafından tanımlandı):**
1. **Tool Fallback** — Gemini'nin mevcut tool'u olmadığında shell ile halleder (AI raw command yazar)
2. **Doğrudan Shell** — Python server açma, script çalıştırma, genel terminal kullanımı

- [ ] Termux bootstrap indirme sistemi (ABI-aware: arm64-v8a / armeabi-v7a / x86_64)
- [ ] proot kurulum + storage bind-mount (`/storage:/storage`)
- [ ] `EmbeddedShell` — persistent session, stdin/stdout/stderr stream
- [ ] `ShellTool` — JarvisTool interface implementasyonu (AI shell fallback için)
- [ ] Terminal UI ekranı (Settings → Power Mode → Terminal)
  - Scrollable output
  - Text input + send button
  - Running process → stop button (SIGTERM/SIGKILL)
- [ ] Power Mode toggle (Settings) + ilk aktivasyon uyarı dialogu
- [ ] Shell güvenlik seviyesi seçici (UNRESTRICTED / CONFIRM_EACH / RESTRICTED)
- [ ] stdout/stderr → Gemini özetleme (opsiyonel, AI-driven komutlarda)
- [ ] Stop/interrupt entegrasyonu (⏹ butonu shell process'i de durdurur)

**Doğrulanması gereken (implement başlamadan önce):**
- Termux bootstrap release URL'leri ve ABI asset adları (Termux GitHub releases)
- proot binary bootstrap içinde mi, yoksa custom NDK build mi?
- `MANAGE_EXTERNAL_STORAGE` permission gereksinimi (SDK 36 hedefinde)

### 4b. Diğer Phase 4 Özellikleri

- [ ] Macro/workflow kaydetme & tekrarlama
- [ ] Cross-app workflow
- [ ] Floating bubble assistant
- [ ] VoiceInteractionService (varsayılan asistan / power button trigger)
- [ ] AssistStructure-based screen context

---

## Phase 5: Henüz Başlanmadı

- [ ] Multi-language auto-detection
- [ ] Proactive suggestions / habit learning
- [ ] Smart notification filtering
- [ ] Light theme
- [ ] Accessibility service aktivasyon rehberi (Phase 3'ten taşındı)
- [ ] Sensitive app blacklist (Phase 3'ten taşındı)

---

## Bilinen Buglar & Problemler

### 1. Groq LLM tool calling — ÇÖZÜLDÜ (commit 980af53)
- Sorun: Groq Llama 3.3 70B native function calling'i malformed XML üretiyordu
- Çözüm: Native `tools` parametresi kaldırıldı, JSON-based tool calling'e geçildi

### 2. Local model generation çok yavaş (çözüm beklemede)
- Llama-3.2-1B-Instruct-Q4_K_M.gguf: prompt >6000 chars → generation >30s
- Çözüm uygulandı: 60s timeout + cancelGeneration(), prompt truncation, maxTokens=128
- Test edilmedi — cihazda denenmeli

### 3. Weather tool hata döndürüyor
- `WEATHER_API_KEY` set olmasına rağmen `get_weather` error dönüyor
- Weatherapi endpoint/kod kontrol edilmeli

### 4. Microphone permission bug
- İlk izin istendiğinde bazen beklemede kalıyor
- Onboarding Screen 2'de tekrar test edilmeli

---

## UX İyileştirmeleri (Biriktirilen)

- [ ] Autonomy Level değiştirirken uyarı dialog'u (özellikle FULL_AUTO)
- [ ] Screen action preview sırasında geri sayım sesi/titreşim
- [ ] İlk tool kullanımında permission dialog'u (PermissionRequiredException)
- [ ] Model indirme iptal edilebilir olmalı
- [ ] İndirme sonrası otomatik model seçimi

---

## Local TTS (XTTS) Planı

**Durum:** Şu anda Edge TTS kullanılıyor (Kokoro dropped — Turkish desteği yok).

**Plan:**
1. **Kısa vade:** Edge TTS devam ediyor
2. **Orta vade (Phase 3-4 bitince):** HF Inference API üzerinden XTTS-v2 entegrasyonu
3. **Uzun vade (Phase 5 sonrası):** On-device TTS araştırması

**Hedef:** XTTS entegrasyonu Eylül 2026.