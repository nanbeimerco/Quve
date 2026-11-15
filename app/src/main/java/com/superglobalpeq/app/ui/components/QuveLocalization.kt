package com.superglobalpeq.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.GlassRenderStyle
import com.superglobalpeq.app.data.PillMotionStyle
import com.superglobalpeq.app.data.QuveLanguage
import com.superglobalpeq.app.data.QuveThemeMode
import com.superglobalpeq.app.data.QuveThemePreset
import com.superglobalpeq.app.dsp.DefaultPresets
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.model.SwapPosition

@Composable
fun QuveLanguage.displayLabel(): String = when (this) {
    QuveLanguage.SYSTEM -> stringResource(R.string.lang_system)
    QuveLanguage.ENGLISH -> stringResource(R.string.lang_english)
    QuveLanguage.JAPANESE -> stringResource(R.string.lang_japanese)
}

@Composable
fun QuveThemeMode.displayLabel(): String = when (this) {
    QuveThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
    QuveThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
    QuveThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
}

@Composable
fun QuveThemePreset.displayLabel(): String = fixedDisplayName ?: when (this) {
    QuveThemePreset.QUVE_VERMILION -> stringResource(R.string.palette_quve_vermilion)
    QuveThemePreset.MIDNIGHT_STUDIO -> stringResource(R.string.palette_midnight_studio)
    QuveThemePreset.SAKURA_NEON -> stringResource(R.string.palette_sakura_neon)
    QuveThemePreset.CYBER_AMBER -> stringResource(R.string.palette_cyber_amber)
    QuveThemePreset.NORDIC_SLATE -> stringResource(R.string.palette_nordic_slate)
    QuveThemePreset.ELECTRIC_VIOLET -> stringResource(R.string.palette_electric_violet)
    else -> key
}

@Composable
fun GlassRenderStyle.displayLabel(): String = when (this) {
    GlassRenderStyle.CLEAR -> stringResource(R.string.glass_style_clear)
    GlassRenderStyle.FROSTED -> stringResource(R.string.glass_style_frosted)
}

@Composable
fun PillMotionStyle.displayLabel(): String = when (this) {
    PillMotionStyle.FAST -> stringResource(R.string.pill_motion_fast)
    PillMotionStyle.FLUID -> stringResource(R.string.pill_motion_fluid)
    PillMotionStyle.RELAXED -> stringResource(R.string.pill_motion_relaxed)
}

@Composable
fun PeqChannelTarget.fullLabel(): String = when (this) {
    PeqChannelTarget.COMMON -> stringResource(R.string.channel_common_label)
    PeqChannelTarget.LEFT -> stringResource(R.string.channel_left_label)
    PeqChannelTarget.RIGHT -> stringResource(R.string.channel_right_label)
}

@Composable
fun PeqChannelTarget.shortSegmentLabel(): String = when (this) {
    PeqChannelTarget.COMMON -> stringResource(R.string.channel_common_short)
    PeqChannelTarget.LEFT -> stringResource(R.string.channel_left_short)
    PeqChannelTarget.RIGHT -> stringResource(R.string.channel_right_short)
}

@Composable
fun SwapPosition.titleLabel(): String = when (this) {
    SwapPosition.BEFORE_INDEPENDENT_EQ -> stringResource(R.string.swap_pos_before_title)
    SwapPosition.AFTER_INDEPENDENT_EQ -> stringResource(R.string.swap_pos_after_title)
}

@Composable
fun SwapPosition.detailLabel(): String = when (this) {
    SwapPosition.BEFORE_INDEPENDENT_EQ -> stringResource(R.string.swap_pos_before_detail)
    SwapPosition.AFTER_INDEPENDENT_EQ -> stringResource(R.string.swap_pos_after_detail)
}

@Composable
fun DeviceCategory.displayLabel(): String = when (this) {
    DeviceCategory.SPEAKER -> stringResource(R.string.device_cat_speaker)
    DeviceCategory.BLUETOOTH -> stringResource(R.string.device_cat_bluetooth)
    DeviceCategory.USB_DAC -> stringResource(R.string.device_cat_usb_dac)
    DeviceCategory.WIRED -> stringResource(R.string.device_cat_wired)
}

@Composable
fun EqPreset.displayName(): String {
    return if (id == DefaultPresets.FLAT_PRESET_ID && name in DefaultPresets.FLAT_DEFAULT_NAMES) {
        stringResource(R.string.preset_flat_name)
    } else {
        name
    }
}

@Composable
fun DeviceRoutingRule.displayDeviceName(): String {
    return if (deviceKey == "speaker" && category == DeviceCategory.SPEAKER) {
        stringResource(R.string.device_built_in_speaker)
    } else {
        displayName
    }
}
