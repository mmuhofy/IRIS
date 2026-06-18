# IRIS — Yapılacaklar & Notlar

---

## Phase 1-2: Tamamlanan

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

## Phase 3: Kısmi — Devam Eden

- [x] AutonomyLevel (SAFE / BALANCED / FULL_AUTO / CUSTOM)
- [x] ScreenInteractionRepository
- [x] IrisAccessibilityService (servis + manifest)
- [x] Screen tools: click, type, scroll, navigate_back, read_screen
- [x] ActionPreviewOverlay (spotlight, countdown, ripple, cancel)
- [x] ScreenActionGate (overlay üzerinden approval)
- [ ] **Autonomy Level picker UI** — Settings'te seçici henüz yok
- [ ] **Accessibility service aktivasyon rehberi** — Kullanıcıya adım adım açıklama ekranı gerek
- [x] **NavigateTool** — label iyileştirildi, koordinatsız (sistem aksiyonu)
- [x] **Accessibility performans fix** — background thread + 150ms debounce + node recycling
- [ ] **Sensitive app blacklist** — bankacılık/şifre uygulamaları için kara liste

## Phase 4: Henüz Başlanmadı

- [ ] Termux entegrasyonu (dosya işlemleri: move, copy, delete, read, write, list, search, rename)
- [ ] Macro/workflow kaydetme & tekrarlama
- [ ] Cross-app workflow
- [ ] Floating bubble assistant
- [ ] VoiceInteractionService (varsayılan asistan / power button trigger)
- [ ] AssistStructure-based screen context

## Phase 5: Henüz Başlanmadı

- [ ] Multi-language auto-detection
- [ ] Proactive suggestions / habit learning
- [ ] Smart notification filtering
- [ ] Light theme

## Bilinen Buglar & Problemler

### 1. Groq LLM tool calling — ÇÖZÜLDÜ (commit 980af53)
- Sorun: Groq Llama 3.3 70B native function calling'i malformed XML üretiyordu (`<function-name...` ile `=` yerine `-`)
- Çözüm: Native `tools` parametresi kaldırıldı, JSON-based tool calling'e geçildi (LocalLlmRepository ile aynı yaklaşım)
- Tool description'lar system prompt'a ekleniyor, model `{"tool": "name", "args": {}}` formatında yanıt veriyor

### 2. Local model generation çok yavaş (çözüm beklemede)
- Llama-3.2-1B-Instruct-Q4_K_M.gguf: prompt >6000 chars → generation >30s
- Çözüm uygulandı: 60s timeout + cancelGeneration(), prompt truncation, maxTokens=128
- Test edilmedi — cihazda denenmeli

### 3. Weather tool hata döndürüyor
- `WEATHER_API_KEY` environment variable set olmasına rağmen `get_weather` error dönüyor
- Weatherapi endpoint/kod kontrol edilmeli

### 4. Microphone permission bug
- İlk izin istendiğinde bazen beklemede kalıyor veya hata dönüyor
- Onboarding Screen 2'de tekrar test edilmeli

## UX İyileştirmeleri

- [ ] Accessibility service aktivasyonu için adım adım rehber ekranı
- [ ] Autonomy Level değiştirirken uyarı dialog'u (özellikle FULL_AUTO)
- [ ] Screen action preview sırasında geri sayım sesi/titreşim
- [ ] İlk tool kullanımında permission dialog'u (PermissionRequiredException)
- [ ] Model indirme iptal edilebilir olmalı
- [ ] İndirme sonrası otomatik model seçimi

---

## Local TTS (XTTS) Planı

**Durum:** Şu anda Kokoro TTS (HuggingFace Inference API) kullanılıyor.

**XTTS-v2 için gerekenler:**
- Model boyutu: ~1.2GB (XTTS-v2) — cihazda yer açılmalı
- HF Inference API: XTTS-v2 destekleniyor (`https://api-inference.huggingface.co/models/coqui/XTTS-v2`)
  - API ile kullanım: Kokoro ile aynı endpoint pattern, sadece model değişir
- On-device: şu an için mümkün değil (llama.cpp ses modelleri henüz stabil değil, mobil için optimize model yok)
- Alternatif: **OuteTTS** (0.5B parametre) — daha küçük, mobil için daha uygun olabilir

**Plan:**
1. **Kısa vade (Phase 1-2):** Kokoro TTS devam ediyor
2. **Orta vade (Phase 3-4 bitince):** HF Inference API üzerinden XTTS-v2 entegrasyonu (ses klonlama için)
   - `MultiSpeakerTtsRepository` veya `TtsProvider` arayüzü
   - Provider seçimi: Kokoro veya XTTS
   - Voice cloning: kullanıcı sesini kaydedip XTTS'e gönderme
3. **Uzun vade (Phase 5 sonrası):** On-device TTS araştırması
   - executorch + XTTS quantized
   - OuteTTS mobil port
   - llama.cpp ses modeli desteği stabil olunca

**Kesin zaman:** XTTS entegrasyonu Phase 3-4 bittiğinde başlayabilir. Şu anki hedef: Eylül 2026.
