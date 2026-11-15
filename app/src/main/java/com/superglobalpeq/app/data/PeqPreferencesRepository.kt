package com.superglobalpeq.app.data

import android.content.Context
import com.superglobalpeq.app.R
import com.superglobalpeq.app.dsp.DefaultPresets
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.DeviceRuleAction
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.SwapPosition
import org.json.JSONArray
import org.json.JSONObject

class PeqPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class PersistedSettings(
        val presets: List<EqPreset>,
        val selectedPresetId: String,
        val bypass: Boolean,
        val channelSwap: Boolean,
        val swapPosition: SwapPosition,
        val deviceRules: List<DeviceRoutingRule>,
        val bypassedPackages: Set<String>
    )

    fun load(): PersistedSettings {
        val localized = QuveSettings.localizedContext(appContext)
        val bypass = prefs.getBoolean(KEY_BYPASS, false)
        val legacySwap = prefs.getBoolean(KEY_CHANNEL_SWAP, false)
        val legacySwapPosName = prefs.getString(KEY_SWAP_POSITION, SwapPosition.BEFORE_INDEPENDENT_EQ.name)
        val legacySwapPos = SwapPosition.entries.firstOrNull { it.name == legacySwapPosName }
            ?: SwapPosition.BEFORE_INDEPENDENT_EQ

        val presetsJson = prefs.getString(KEY_PRESETS, null)
        var migratedAny = false
        var presets = if (presetsJson != null) {
            parsePresetsWithMigration(presetsJson, legacySwap, legacySwapPos) { migrated ->
                migratedAny = migrated
            }.ifEmpty { defaultPresetList(localized) }
        } else {
            defaultPresetList(localized)
        }

        val removedBuiltin = !prefs.getBoolean(KEY_REMOVED_BUILTIN_IG955_V1, false)
        if (removedBuiltin) {
            prefs.edit().putBoolean(KEY_REMOVED_BUILTIN_IG955_V1, true).apply()
            val filtered = presets.filterNot { it.id == LEGACY_IG955_PRESET_ID }
            if (filtered.size != presets.size) {
                presets = filtered.ifEmpty { defaultPresetList(localized) }
                migratedAny = true
            }
        }
        if (migratedAny) {
            savePresets(presets)
        }

        val savedSelected = prefs.getString(KEY_SELECTED_PRESET_ID, DefaultPresets.FLAT_PRESET_ID)
        val selectedId = if (presets.any { it.id == savedSelected }) {
            savedSelected!!
        } else {
            presets.first().id
        }
        val activePreset = presets.first { it.id == selectedId }

        val rulesJson = prefs.getString(KEY_DEVICE_RULES, null)
        var rules = if (rulesJson != null) {
            parseDeviceRules(rulesJson)
        } else {
            listOf(
                DeviceRoutingRule(
                    deviceKey = "speaker",
                    displayName = localized.getString(R.string.device_built_in_speaker),
                    category = DeviceCategory.SPEAKER,
                    action = DeviceRuleAction.FOLLOW_MANUAL
                )
            )
        }
        if (removedBuiltin) {
            var rulesChanged = false
            rules = rules.map { rule ->
                if (rule.action == DeviceRuleAction.PRESET && rule.presetId == LEGACY_IG955_PRESET_ID) {
                    rulesChanged = true
                    rule.copy(action = DeviceRuleAction.FOLLOW_MANUAL, presetId = null)
                } else {
                    rule
                }
            }
            if (rulesChanged) {
                saveDeviceRules(rules)
            }
        }

        val bypassedPackages = parseBypassedPackagesJson(prefs.getString(KEY_BYPASSED_PACKAGES, null))

        return PersistedSettings(
            presets = presets,
            selectedPresetId = selectedId,
            bypass = bypass,
            channelSwap = activePreset.channelSwap,
            swapPosition = activePreset.swapPosition,
            deviceRules = rules,
            bypassedPackages = bypassedPackages
        )
    }

    fun savePresets(presets: List<EqPreset>) {
        val arr = JSONArray().apply { presets.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    fun saveSelectedPresetId(presetId: String) {
        prefs.edit().putString(KEY_SELECTED_PRESET_ID, presetId).apply()
    }

    fun saveBypass(bypass: Boolean) {
        prefs.edit().putBoolean(KEY_BYPASS, bypass).apply()
    }

    fun saveChannelSwap(swap: Boolean, position: SwapPosition) {
        prefs.edit()
            .putBoolean(KEY_CHANNEL_SWAP, swap)
            .putString(KEY_SWAP_POSITION, position.name)
            .apply()
    }

    fun saveDeviceRules(rules: List<DeviceRoutingRule>) {
        val arr = JSONArray().apply { rules.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_DEVICE_RULES, arr.toString()).apply()
    }

    fun saveBypassedPackages(packages: Set<String>) {
        prefs.edit()
            .putString(KEY_BYPASSED_PACKAGES, serializeBypassedPackagesJson(packages))
            .apply()
    }

    private fun defaultPresetList(localized: Context): List<EqPreset> = listOf(
        DefaultPresets.createFlatPreset(localized.getString(R.string.preset_flat_name))
    )

    private fun parsePresetsWithMigration(
        json: String,
        legacySwap: Boolean,
        legacySwapPos: SwapPosition,
        onMigrated: (Boolean) -> Unit
    ): List<EqPreset> = try {
        val arr = JSONArray(json)
        var anyMigrated = false
        val list = buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                var (parsed, hasSwapKey) = EqPreset.fromJsonWithSwapFlag(obj)
                if (!hasSwapKey) {
                    anyMigrated = true
                    parsed = parsed.copy(channelSwap = legacySwap, swapPosition = legacySwapPos)
                }
                if (parsed.baseline == null && parsed.id == DefaultPresets.FLAT_PRESET_ID) {
                    anyMigrated = true
                    parsed = parsed.copy(baseline = DefaultPresets.createFlatPreset().baseline)
                }
                add(parsed)
            }
        }
        onMigrated(anyMigrated)
        list
    } catch (_: Throwable) {
        emptyList()
    }

    private fun parseDeviceRules(json: String): List<DeviceRoutingRule> = try {
        val arr = JSONArray(json)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(DeviceRoutingRule.fromJson(obj))
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    companion object {
        private const val PREFS_NAME = "super_global_peq_prefs"
        private const val KEY_PRESETS = "presets_v1"
        private const val KEY_SELECTED_PRESET_ID = "selected_preset_id_v1"
        private const val KEY_BYPASS = "bypass_v1"
        private const val KEY_CHANNEL_SWAP = "channel_swap_v1"
        private const val KEY_SWAP_POSITION = "swap_position_v1"
        private const val KEY_DEVICE_RULES = "device_rules_v1"
        private const val KEY_BYPASSED_PACKAGES = "bypassed_packages_v1"
        private const val KEY_REMOVED_BUILTIN_IG955_V1 = "migrated_removed_builtin_ig955_v1"
        private const val LEGACY_IG955_PRESET_ID = "preset_ig955_11_13"

        fun serializeBypassedPackagesJson(packages: Set<String>): String {
            val arr = JSONArray()
            packages.map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .forEach { arr.put(it) }
            return arr.toString()
        }

        fun parseBypassedPackagesJson(json: String?): Set<String> {
            if (json.isNullOrBlank()) return emptySet()
            return try {
                val arr = JSONArray(json)
                val out = LinkedHashSet<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i).trim()
                    if (pkg.isNotEmpty()) out.add(pkg)
                }
                out
            } catch (_: Throwable) {
                emptySet()
            }
        }
    }
}
