# Changelog

All notable changes to IRIS are documented in this file.

## [Unreleased]

## [0.4.0] - 2026-06-26

### Features
- Add git-based automatic versionCode and versionName
- Add Termux file-operation tools (move, copy, delete, read, write, list, search, rename)
- Add alarm, reminder, and calendar productivity tools
- Add call, SMS, and notification communication tools
- Add volume, brightness, wifi, bluetooth, and app-launch system tools
- Add weather, web search, and news information tools
- Add screen reading via AccessibilityService
- Add screen control tools (click, type, scroll, navigate)
- Add ActionPreviewOverlay with countdown and cancel
- Add Autonomy Level system (SAFE, BALANCED, FULL_AUTO, CUSTOM)
- Add sensitive app blacklist for screen control
- Add onboarding flow with 5 sequential screens
- Add theme system with dark mode and 6 color schemes
- Add Iris Core animation with IDLE, LISTENING, THINKING, SPEAKING states
- Add local conversation history via Room
- Add Wake Word detection via openWakeWord
- Add Groq Whisper STT with Turkish language support
- Add Gemini 3.5 Flash chat integration
- Add Kokoro TTS via HuggingFace Inference API
- Add foreground service for background wake-word listening
- Add bootstrap provisioning for Termux runtime environment
- Add Power Mode screen with battery optimization controls
- Add data settings screen with conversation management

### Bug Fixes
- Stream bootstrap extraction from file instead of loading 58MB into memory
- Stream bootstrap download directly to file to avoid OOM
- Correct Elf64_Dyn entry stride (16 bytes, not 24)
- Remove broken addAndroidNote from bootstrap
- Lower targetSdk to 28 for SELinux exec compatibility on Android 15
- Patch hardcoded Termux paths in bash binary to fix SYSCONFDIR
- Remove LD_PRELOAD causing CANNOT LINK error on bootstrap bash
- Fix Groq Whisper multipart request content type

### Refactoring
- Set Cobalt as default color scheme
- Soften palettes for eye comfort, add dark toolbar button bg
- Remove remaining IrisCard reference in BootstrapStatusCard
- Update PowerModeScreen card styling to current theme
- Increase toolbar horizontal padding from 6dp to 16dp

### CI/CD
- Add GitHub Actions workflows for debug, release, and bootstrap builds
- Add NDK source files to CI cache key
- Extract common setup to reusable action
- Add concurrency and release upload

[Unreleased]: https://github.com/mmuhofy/IRIS/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/mmuhofy/IRIS/releases/tag/v0.4.0
