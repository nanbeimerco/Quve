# Third-Party Licenses & Attributions

Quve is licensed under the **GNU General Public License v3.0 (GPL-3.0)** (see [LICENSE](LICENSE)).
Below are the third-party projects, libraries, assets, and design inspirations incorporated into or referenced by Quve.

---

## GPL-3.0 Components & Inspirations

1. **RootlessJamesDSP** — https://github.com/ThePBone/RootlessJamesDSP  
   - **License:** GNU General Public License v3.0 (GPL-3.0)  
   - **Usage in Quve:** Color schemes and palette tokens for themes 7–14 (`Monet`, `Teal Turquoise`, `Green Apple`, `Honey`, `Strawberry Daiquiri`, `Tidal Wave`, `Yin & Yang`, `Yotsuba`) in `Theme.kt` and `QuveSettings.kt` (adapted in RootlessJamesDSP from Tachiyomi/Mihon, Apache-2.0), plus architectural inspiration for non-root system-wide Android audio processing.

2. **EchoMusicApp / Echo-Music**  
   - **License:** GNU General Public License v3.0 (GPL-3.0)  
   - **Usage in Quve:** Adapted Liquid Glass modifier integration in `LiquidGlass.kt` and morphing tab-bar modifications in `FloatingTabBar.kt`.

3. **Equalizer 314** — https://github.com/bearinmindcat/Equalizer314  
   - **License:** GNU General Public License v3.0 (GPL-3.0)  
   - **Usage in Quve:** Conceptual inspiration for parametric EQ editing UX, Equalizer APO text import/export workflows, headroom balancing, and output-device switching workflows. (Quve's audio engine itself is independently implemented using Shizuku `AudioPolicy` loopback and 64-bit TDF-II Kotlin biquad processing rather than `DynamicsProcessing` session effects.)

---

## Permissive Libraries & Vendored Components (Apache-2.0 / MIT / ISC)

4. **Kyant0 / backdrop (`v2.0.0`)** — https://github.com/Kyant0/backdrop  
   - **License:** Apache License 2.0  
   - **Copyright:** Copyright 2025 Kyant0  
   - **Usage in Quve:** Vendored Liquid Glass runtime-shader & backdrop rendering stack in `app/src/main/java/com/superglobalpeq/app/ui/components/backdrop/`, with local Quve shader/refraction additions (`OpticalLens.kt`, `ClearRefraction`, etc.).

5. **compose-floating-tab-bar (`v1.0.1`)** — https://github.com/elyesmansour/compose-floating-tab-bar  
   - **License:** Apache License 2.0  
   - **Copyright:** Elyes Mansour  
   - **Usage in Quve:** Vendored base floating tab bar component in `app/src/main/java/com/superglobalpeq/app/ui/components/floatingtabbar/FloatingTabBar.kt` (with Echo-Music and local modifications noted in the file header).

6. **liquid_glass_easy (`4.3.4`)** — https://pub.dev/packages/liquid_glass_easy  
   - **License:** MIT License  
   - **Copyright:** Copyright (c) 2025 Ahmed Gamil  
   - **Usage in Quve:** Selection pill lens motion physics and spring/squash constants ported in `app/src/main/java/com/superglobalpeq/app/ui/components/floatingtabbar/GlassSelectionPill.kt`.

7. **Shizuku API (`13.1.5`)** — https://github.com/RikkaApps/Shizuku-API  
   - **License:** Apache License 2.0  
   - **Usage in Quve:** `dev.rikka.shizuku:api` and `dev.rikka.shizuku:provider` dependencies used to bind the privileged shell (`UID 2000`) loopback service without root.

8. **Lucide Icons** — https://lucide.dev  
   - **License:** ISC License (with portions derived from Feather Icons, MIT License, Copyright (c) 2013-2022 Cole Bemis)  
   - **Usage in Quve:** Vector icon path definitions in `app/src/main/java/com/superglobalpeq/app/ui/icons/LucideIcons.kt`.

9. **AndroidX / Jetpack Compose / Material 3 Expressive**  
   - **License:** Apache License 2.0
