# Quve

**Quve** is a system-wide, 64-bit Parametric Equalizer (PEQ) for **non-rooted Android devices** powered by **Shizuku** and Android's `AudioPolicy` exclusive loopback routing.

Licensed under the **GNU General Public License v3.0 (GPL-3.0)** — see [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

## Features

### Audio & DSP Engine
- **System-Wide Exclusive Loopback (No Root, No MediaProjection Dialog)**  
  Runs a privileged `com.android.shell` (`UID 2000`) `UserService` via Shizuku and registers an Android `AudioPolicy` (`ROUTE_FLAG_LOOP_BACK`) capturing `USAGE_MEDIA`, `USAGE_GAME`, and `USAGE_UNKNOWN` audio without dual playback.
- **64-bit Stereo TDF-II Biquad Processing**  
  Processes 48 kHz stereo PCM audio in 10 ms blocks through double-precision Transposed Direct Form II biquad filters with soft clipping protection, then outputs via a low-latency `AudioTrack`.
- **Common + Independent Left / Right EQ**  
  Configure independent EQ chains for:
  - **Common (L+R)** — Shared tonal tuning across both channels
  - **Left (HW Calib.)** & **Right (HW Calib.)** — Per-channel calibration for driver sensitivity mismatch or asymmetric acoustics
- **8 Parametric Filter Types**  
  `PK` (Peaking), `LSC` (Low Shelf), `HSC` (High Shelf), `LP` (Low Pass), `HP` (High Pass), `NO` (Notch), `BP` (Band Pass), and `AP` (All Pass), with numeric dialog editing (`Fc`, `Gain`, `Q`) and **Auto 0dB** preamp headroom normalization.
- **Real-Time Frequency Response Graph**  
  Plots `Common L+R`, `Total L`, and `Total R` curves with peak gain / bypass indicators.

### Presets & Equalizer APO Compatibility
- **Equalizer APO (`.txt`) Import & Export**  
  Import `.txt` files or paste raw Equalizer APO configuration lines, including multi-channel `Channel: L` / `Channel: R` sections, `Preamp`, and Quve channel-swap metadata (`# Quve-ChannelSwap`, `# Quve-SwapPosition`). Export full presets back to `.txt`.
- **Preset Edit Tracking**  
  Automatically tracks edits relative to the imported or baseline preset (`Edited` indicator), with 1-tap **Revert to Original** and **Save as New**.

### Routing, Channel Swap & App Bypass
- **Per-Preset L⇄R Channel Swap**  
  Swap left and right audio channels per preset, with configurable DSP stage placement (**Before** or **After** independent L/R EQ to keep driver calibration locked to physical earpieces).
- **Output Device Auto-Switch**  
  Monitors Built-in Speaker, Bluetooth, USB DAC, and Wired/Line Out changes to automatically apply an assigned preset, follow manual selection, or switch to **EQ OFF (Bypass)**.
- **Per-App Bypass (`excludeMixRule`)**  
  Pick specific installed apps (with search and `Standard Apps` / `All (+System)` filter) whose UIDs are excluded from `AudioPolicy` capture so they play cleanly through Android's standard audio path with zero DSP or added latency.

### UI & Customization
- **Material 3 Expressive & Liquid Glass UI**  
  Features animated Liquid Glass navigation bar & bottom sheets, real-time refraction/blur/clarity controls, customizable pill motion & haptic feedback, **14 color palettes** (6 Quve Expressive palettes + 8 RootlessJamesDSP-inspired palettes), Light/Dark/System themes, and **AMOLED Pure Black** mode.
- **Multi-Language Support**  
  Full **English (`en`)** and **Japanese (`ja`)** localization, following the system locale by default or switchable in-app.

---

## Tested Devices

Verified working on the following devices:
- **vivo X Fold3 Pro** (Android 16)
- **Xiaomi Pad 6** (Android 14 / HyperOS)
- **Samsung Galaxy S21**

---

## Requirements

- **Android 13+** (`minSdk = 33`, `targetSdk = 37`)
- **[Shizuku](https://shizuku.rikka.app/)** installed and running (via Wireless Debugging, ADB, or Root), with permission granted to Quve

---

## How System-Wide EQ Works

1. The app binds `AudioLoopbackUserService` inside Shizuku's `com.android.shell` (`UID 2000`) process over AIDL.
2. Inside the `UID 2000` service, an `AudioMixingRule` + `AudioMix` (`ROUTE_FLAG_LOOP_BACK`) + `AudioPolicy` is registered with `AudioManager.registerAudioPolicyStatic`.
3. `AudioMixingRule.Builder.excludeMixRule(RULE_MATCH_UID, ...)` excludes Quve's own output (`UID 2000`), the UI client UID, and any user-selected bypassed app UIDs to prevent feedback loops and allow per-app bypass.
4. `AudioRecord` captures redirected system PCM audio (`48 kHz`, stereo, `PCM_16BIT`), runs `StereoPeqProcessor`, and writes the processed stream to a low-latency `AudioTrack`.

> **Note:** Capture behavior depends on Android's `AudioPolicy` routing and OEM audio architecture. Apps or output paths that bypass standard `USAGE_MEDIA` / `USAGE_GAME` / `USAGE_UNKNOWN` software mixing may not be intercepted on all devices. Disabling battery optimization for both Quve and Shizuku is recommended for uninterrupted background playback.

---

## Building from Source

### Prerequisites
- JDK 17
- Android SDK with platform **API 37** (`compileSdk = 37`)

### Build & Test Commands
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build R8-optimized release APK
./gradlew assembleRelease
```

The release APK is generated at `app/build/outputs/apk/release/app-release.apk` (currently configured with the debug signing config in `app/build.gradle.kts` for local builds).

---

## License & Acknowledgements

Quve is free software distributed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for the full license text and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for all upstream credits and permissive component licenses, including:
- **[RootlessJamesDSP](https://github.com/ThePBone/RootlessJamesDSP)** (GPL-3.0) — Theme color palettes & non-root system-wide EQ inspiration
- **[Equalizer 314](https://github.com/bearinmindcat/Equalizer314)** (GPL-3.0) — Parametric EQ workflow & Equalizer APO UX inspiration
- **EchoMusicApp / Echo-Music** (GPL-3.0) — Liquid Glass & floating tab bar adaptations
- **[Kyant0/backdrop](https://github.com/Kyant0/backdrop)** (Apache-2.0) — Vendored Liquid Glass shader & backdrop engine
- **[compose-floating-tab-bar](https://github.com/elyesmansour/compose-floating-tab-bar)** (Apache-2.0) & **liquid_glass_easy** (MIT) — Floating tab bar & pill physics
- **[Shizuku API](https://github.com/RikkaApps/Shizuku-API)** (Apache-2.0) & **[Lucide Icons](https://lucide.dev)** (ISC)
