package com.superglobalpeq.app.data

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class QuveLanguage(val key: String, val languageTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    JAPANESE("ja", "ja"),
    ;

    companion object {
        fun fromLanguageTag(tag: String?): QuveLanguage {
            val normalized = tag?.trim()?.lowercase(Locale.US).orEmpty()
            return when {
                normalized.startsWith("ja") -> JAPANESE
                normalized.startsWith("en") -> ENGLISH
                else -> SYSTEM
            }
        }
    }
}

/**
 * Quve theme modes. DARK is default per user preference (Dark AMOLED black + orange).
 */
enum class QuveThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

/**
 * 14 Material 3 Expressive color palettes:
 * - Quve Expressive (6 palettes, default = QUVE_VERMILION orange & black)
 * - RootlessJamesDSP inspired palettes (8 palettes)
 */
enum class QuveThemePreset(
    val key: String,
    val fixedDisplayName: String?,
    val isRootlessPalette: Boolean,
) {
    // ── Quve Expressive Presets ─────────────────────────────────────────
    QUVE_VERMILION("quve_vermilion", null, false),
    MIDNIGHT_STUDIO("midnight_studio", null, false),
    SAKURA_NEON("sakura_neon", null, false),
    CYBER_AMBER("cyber_amber", null, false),
    NORDIC_SLATE("nordic_slate", null, false),
    ELECTRIC_VIOLET("electric_violet", null, false),

    // ── RootlessJamesDSP Presets ────────────────────────────────────────
    MONET("monet", "Monet (Dynamic)", true),
    TEAL_TURQUOISE("teal_turquoise", "Teal Turquoise", true),
    GREEN_APPLE("green_apple", "Green Apple", true),
    HONEY("honey", "Honey", true),
    STRAWBERRY_DAIQUIRI("strawberry_daiquiri", "Strawberry Daiquiri", true),
    TIDAL_WAVE("tidal_wave", "Tidal Wave", true),
    YIN_YANG("yin_yang", "Yin & Yang", true),
    YOTSUBA("yotsuba", "Yotsuba", true),
    ;

    companion object {
        val DEFAULT = QUVE_VERMILION

        fun fromKey(key: String?): QuveThemePreset =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class GlassRenderStyle(val key: String) {
    CLEAR("clear"),
    FROSTED("frosted"),
    ;

    companion object {
        val DEFAULT = CLEAR

        fun fromKey(key: String?): GlassRenderStyle =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class PillMotionStyle {
    FAST,
    FLUID,
    RELAXED,
    ;

    companion object {
        val DEFAULT = FLUID
    }
}

const val DEFAULT_GLASS_CLARITY = 0.75f
const val DEFAULT_GLASS_SHADE = 0.50f
const val DEFAULT_GLASS_REFRACTION = 1.00f
const val DEFAULT_LIQUID_GLASS_BLUR_DP = 14f
const val DEFAULT_LIQUID_GLASS_OPACITY = 0.24f

/**
 * Process-wide reactive Appearance + Liquid Glass settings store (backed by SharedPreferences).
 */
object QuveSettings {
    private const val PREFS_NAME = "quve_appearance_prefs"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_PRESET = "theme_preset"
    private const val KEY_AMOLED_BLACK = "amoled_black"
    private const val KEY_GLASS_STYLE = "glass_render_style"
    private const val KEY_GLASS_CLARITY = "glass_clarity"
    private const val KEY_GLASS_SHADE = "glass_shade"
    private const val KEY_GLASS_REFRACTION = "glass_refraction"
    private const val KEY_GLASS_BLUR_DP = "glass_blur_dp"
    private const val KEY_GLASS_OPACITY = "glass_opacity"
    private const val KEY_PILL_MOTION = "pill_motion_style"
    private const val KEY_LOCK_BAR_EXPANDED = "lock_bottom_bar_expanded"
    private const val KEY_HIDE_NAV_LABELS = "hide_nav_labels"
    private const val KEY_BOTTOM_BAR_BOUNCE = "bottom_bar_bounce"
    private const val KEY_REDUCE_BLUR = "reduce_dynamic_blur"
    private const val KEY_REDUCE_ANIM = "reduce_animation"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _appLanguage = MutableStateFlow(QuveLanguage.SYSTEM)
    val appLanguage: StateFlow<QuveLanguage> = _appLanguage.asStateFlow()

    private val _themeMode = MutableStateFlow(QuveThemeMode.DARK)
    val themeMode: StateFlow<QuveThemeMode> = _themeMode.asStateFlow()

    private val _themePreset = MutableStateFlow(QuveThemePreset.DEFAULT)
    val themePreset: StateFlow<QuveThemePreset> = _themePreset.asStateFlow()

    private val _amoledBlack = MutableStateFlow(true)
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    private val _glassRenderStyle = MutableStateFlow(GlassRenderStyle.DEFAULT)
    val glassRenderStyle: StateFlow<GlassRenderStyle> = _glassRenderStyle.asStateFlow()

    private val _glassClarity = MutableStateFlow(DEFAULT_GLASS_CLARITY)
    val glassClarity: StateFlow<Float> = _glassClarity.asStateFlow()

    private val _glassShade = MutableStateFlow(DEFAULT_GLASS_SHADE)
    val glassShade: StateFlow<Float> = _glassShade.asStateFlow()

    private val _glassRefraction = MutableStateFlow(DEFAULT_GLASS_REFRACTION)
    val glassRefraction: StateFlow<Float> = _glassRefraction.asStateFlow()

    private val _liquidGlassBlurDp = MutableStateFlow(DEFAULT_LIQUID_GLASS_BLUR_DP)
    val liquidGlassBlurDp: StateFlow<Float> = _liquidGlassBlurDp.asStateFlow()

    private val _liquidGlassOpacity = MutableStateFlow(DEFAULT_LIQUID_GLASS_OPACITY)
    val liquidGlassOpacity: StateFlow<Float> = _liquidGlassOpacity.asStateFlow()

    private val _pillMotionStyle = MutableStateFlow(PillMotionStyle.DEFAULT)
    val pillMotionStyle: StateFlow<PillMotionStyle> = _pillMotionStyle.asStateFlow()

    private val _lockBottomBarExpanded = MutableStateFlow(false)
    val lockBottomBarExpanded: StateFlow<Boolean> = _lockBottomBarExpanded.asStateFlow()

    private val _hideNavigationBarLabels = MutableStateFlow(false)
    val hideNavigationBarLabels: StateFlow<Boolean> = _hideNavigationBarLabels.asStateFlow()

    private val _bottomBarBounce = MutableStateFlow(false)
    val bottomBarBounce: StateFlow<Boolean> = _bottomBarBounce.asStateFlow()

    private val _reduceDynamicBlur = MutableStateFlow(false)
    val reduceDynamicBlur: StateFlow<Boolean> = _reduceDynamicBlur.asStateFlow()

    private val _reduceAnimation = MutableStateFlow(false)
    val reduceAnimation: StateFlow<Boolean> = _reduceAnimation.asStateFlow()

    fun initialize(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        _themeMode.value = sp.getString(KEY_THEME_MODE, null)?.let { name ->
            runCatching { QuveThemeMode.valueOf(name) }.getOrNull()
        } ?: QuveThemeMode.DARK

        _themePreset.value = QuveThemePreset.fromKey(sp.getString(KEY_THEME_PRESET, null))
        _amoledBlack.value = sp.getBoolean(KEY_AMOLED_BLACK, true)
        _glassRenderStyle.value = GlassRenderStyle.fromKey(sp.getString(KEY_GLASS_STYLE, null))
        _glassClarity.value = sp.getFloat(KEY_GLASS_CLARITY, DEFAULT_GLASS_CLARITY).coerceIn(0f, 1f)
        _glassShade.value = sp.getFloat(KEY_GLASS_SHADE, DEFAULT_GLASS_SHADE).coerceIn(0f, 1f)
        _glassRefraction.value = sp.getFloat(KEY_GLASS_REFRACTION, DEFAULT_GLASS_REFRACTION).coerceIn(0f, 1.5f)
        _liquidGlassBlurDp.value = sp.getFloat(KEY_GLASS_BLUR_DP, DEFAULT_LIQUID_GLASS_BLUR_DP).coerceIn(0f, 32f)
        _liquidGlassOpacity.value = sp.getFloat(KEY_GLASS_OPACITY, DEFAULT_LIQUID_GLASS_OPACITY).coerceIn(0.05f, 0.70f)
        _pillMotionStyle.value = sp.getString(KEY_PILL_MOTION, null)?.let { name ->
            runCatching { PillMotionStyle.valueOf(name) }.getOrNull()
        } ?: PillMotionStyle.DEFAULT
        _lockBottomBarExpanded.value = sp.getBoolean(KEY_LOCK_BAR_EXPANDED, false)
        _hideNavigationBarLabels.value = sp.getBoolean(KEY_HIDE_NAV_LABELS, false)
        _bottomBarBounce.value = sp.getBoolean(KEY_BOTTOM_BAR_BOUNCE, false)
        _reduceDynamicBlur.value = sp.getBoolean(KEY_REDUCE_BLUR, false)
        _reduceAnimation.value = sp.getBoolean(KEY_REDUCE_ANIM, false)
        syncLocaleFromSystem(context)
    }

    fun syncLocaleFromSystem(context: Context) {
        val lm = context.getSystemService(LocaleManager::class.java)
        val appLocales = lm?.applicationLocales
        val tag = if (appLocales != null && !appLocales.isEmpty) {
            appLocales.get(0)?.toLanguageTag()
        } else {
            null
        }
        _appLanguage.value = QuveLanguage.fromLanguageTag(tag)
    }

    fun setAppLanguage(context: Context, language: QuveLanguage) {
        _appLanguage.value = language
        val lm = context.getSystemService(LocaleManager::class.java)
        val list = if (language.languageTag != null) {
            LocaleList.forLanguageTags(language.languageTag)
        } else {
            LocaleList.getEmptyLocaleList()
        }
        lm?.applicationLocales = list
    }

    fun localizedContext(base: Context): Context {
        val locales = when (_appLanguage.value) {
            QuveLanguage.ENGLISH -> LocaleList.forLanguageTags("en")
            QuveLanguage.JAPANESE -> LocaleList.forLanguageTags("ja")
            QuveLanguage.SYSTEM -> {
                val lm = base.getSystemService(LocaleManager::class.java)
                val appLocales = lm?.applicationLocales
                if (appLocales != null && !appLocales.isEmpty) {
                    appLocales
                } else {
                    Resources.getSystem().configuration.locales
                }
            }
        }
        val config = Configuration(base.resources.configuration).apply {
            setLocales(locales)
        }
        return base.createConfigurationContext(config)
    }

    fun setThemeMode(mode: QuveThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setThemePreset(preset: QuveThemePreset) {
        _themePreset.value = preset
        prefs?.edit()?.putString(KEY_THEME_PRESET, preset.key)?.apply()
    }

    fun setAmoledBlack(enabled: Boolean) {
        _amoledBlack.value = enabled
        prefs?.edit()?.putBoolean(KEY_AMOLED_BLACK, enabled)?.apply()
    }

    fun setGlassRenderStyle(style: GlassRenderStyle) {
        _glassRenderStyle.value = style
        prefs?.edit()?.putString(KEY_GLASS_STYLE, style.key)?.apply()
    }

    fun setGlassClarity(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _glassClarity.value = v
        prefs?.edit()?.putFloat(KEY_GLASS_CLARITY, v)?.apply()
    }

    fun setGlassShade(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _glassShade.value = v
        prefs?.edit()?.putFloat(KEY_GLASS_SHADE, v)?.apply()
    }

    fun setGlassRefraction(value: Float) {
        val v = value.coerceIn(0f, 1.5f)
        _glassRefraction.value = v
        prefs?.edit()?.putFloat(KEY_GLASS_REFRACTION, v)?.apply()
    }

    fun setLiquidGlassBlurDp(value: Float) {
        val v = value.coerceIn(0f, 32f)
        _liquidGlassBlurDp.value = v
        prefs?.edit()?.putFloat(KEY_GLASS_BLUR_DP, v)?.apply()
    }

    fun setLiquidGlassOpacity(value: Float) {
        val v = value.coerceIn(0.05f, 0.70f)
        _liquidGlassOpacity.value = v
        prefs?.edit()?.putFloat(KEY_GLASS_OPACITY, v)?.apply()
    }

    fun setPillMotionStyle(style: PillMotionStyle) {
        _pillMotionStyle.value = style
        prefs?.edit()?.putString(KEY_PILL_MOTION, style.name)?.apply()
    }

    fun setLockBottomBarExpanded(value: Boolean) {
        _lockBottomBarExpanded.value = value
        prefs?.edit()?.putBoolean(KEY_LOCK_BAR_EXPANDED, value)?.apply()
    }

    fun setHideNavigationBarLabels(value: Boolean) {
        _hideNavigationBarLabels.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE_NAV_LABELS, value)?.apply()
    }

    fun setBottomBarBounce(value: Boolean) {
        _bottomBarBounce.value = value
        prefs?.edit()?.putBoolean(KEY_BOTTOM_BAR_BOUNCE, value)?.apply()
    }

    fun setReduceDynamicBlur(value: Boolean) {
        _reduceDynamicBlur.value = value
        prefs?.edit()?.putBoolean(KEY_REDUCE_BLUR, value)?.apply()
    }

    fun setReduceAnimation(value: Boolean) {
        _reduceAnimation.value = value
        prefs?.edit()?.putBoolean(KEY_REDUCE_ANIM, value)?.apply()
    }

    fun resetLiquidGlassParams() {
        setGlassRenderStyle(GlassRenderStyle.DEFAULT)
        setGlassClarity(DEFAULT_GLASS_CLARITY)
        setGlassShade(DEFAULT_GLASS_SHADE)
        setGlassRefraction(DEFAULT_GLASS_REFRACTION)
        setLiquidGlassBlurDp(DEFAULT_LIQUID_GLASS_BLUR_DP)
        setLiquidGlassOpacity(DEFAULT_LIQUID_GLASS_OPACITY)
        setPillMotionStyle(PillMotionStyle.DEFAULT)
        setLockBottomBarExpanded(false)
        setHideNavigationBarLabels(false)
        setBottomBarBounce(false)
        setReduceDynamicBlur(false)
        setReduceAnimation(false)
    }
}
