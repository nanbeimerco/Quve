package com.superglobalpeq.app.data

import android.content.Context
import com.superglobalpeq.app.R
import com.superglobalpeq.app.dsp.DefaultPresets
import com.superglobalpeq.app.model.AppAudioMode
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceIndependentRuleAction
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.DeviceRuleAction
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.IndependentPeqBaseline
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.SwapPosition
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PeqPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class PersistedSettings(
        val presets: List<EqPreset>,
        val selectedPresetId: String,
        val independentPresets: List<IndependentPeqPreset>,
        val selectedIndependentPresetId: String?,
        val bypass: Boolean,
        val channelSwap: Boolean,
        val swapPosition: SwapPosition,
        val deviceRules: List<DeviceRoutingRule>,
        val bypassedPackages: Set<String>,
        val globalLowLatency: Boolean = false,
        val appAudioModes: Map<String, AppAudioMode> = emptyMap(),
        val backgroundPersistenceEnabled: Boolean = false,
        val masterPowerOn: Boolean = false
    )

    data class SplitMigrationResult(
        val presets: List<EqPreset>,
        val independentPresets: List<IndependentPeqPreset>,
        val selectedIndependentPresetId: String?,
        val deviceRules: List<DeviceRoutingRule>,
        val channelSwap: Boolean,
        val swapPosition: SwapPosition
    )

    fun load(): PersistedSettings {
        val localized = QuveSettings.localizedContext(appContext)
        val bypass = prefs.getBoolean(KEY_BYPASS, false)
        var channelSwap = prefs.getBoolean(KEY_CHANNEL_SWAP, false)
        val legacySwapPosName = prefs.getString(KEY_SWAP_POSITION, SwapPosition.BEFORE_INDEPENDENT_EQ.name)
        var swapPos = SwapPosition.entries.firstOrNull { it.name == legacySwapPosName }
            ?: SwapPosition.BEFORE_INDEPENDENT_EQ

        val presetsJson = prefs.getString(KEY_PRESETS, null)
        var migratedAny = false
        var presets = if (presetsJson != null) {
            parsePresetsWithMigration(presetsJson, channelSwap, swapPos) { migrated ->
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

        var independentPresets = parseIndependentPresets(prefs.getString(KEY_INDEPENDENT_PRESETS, null))

        val savedSelected = prefs.getString(KEY_SELECTED_PRESET_ID, DefaultPresets.FLAT_PRESET_ID)
        val selectedId = if (presets.any { it.id == savedSelected }) {
            savedSelected!!
        } else {
            presets.first().id
        }
        var selectedIndepId = prefs.getString(KEY_SELECTED_INDEPENDENT_PRESET_ID, null)
            ?.takeIf { id -> independentPresets.any { it.id == id } }

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

        val needSplitMigration = !prefs.getBoolean(KEY_MIGRATED_SPLIT_PRESETS_V1, false)
        if (needSplitMigration) {
            val activePreSplit = presets.first { it.id == selectedId }
            val result = migrateLegacyBundles(
                presets = presets,
                existingIndependentPresets = independentPresets,
                deviceRules = rules,
                selectedPresetId = selectedId,
                existingSelectedIndependentId = selectedIndepId,
                fallbackSwap = activePreSplit.channelSwap,
                fallbackSwapPosition = activePreSplit.swapPosition
            )
            presets = result.presets
            independentPresets = result.independentPresets
            selectedIndepId = result.selectedIndependentPresetId
            rules = result.deviceRules
            channelSwap = result.channelSwap
            swapPos = result.swapPosition
            saveIndependentPresets(independentPresets)
            saveSelectedIndependentPresetId(selectedIndepId)
            saveDeviceRules(rules)
            saveChannelSwap(channelSwap, swapPos)
            prefs.edit().putBoolean(KEY_MIGRATED_SPLIT_PRESETS_V1, true).apply()
            migratedAny = true
        }

        if (migratedAny) {
            savePresets(presets)
        }

        val globalLowLatency = prefs.getBoolean(KEY_GLOBAL_LOW_LATENCY, false)
        val appAudioModes = if (prefs.contains(KEY_APP_AUDIO_MODES)) {
            parseAppAudioModesJson(prefs.getString(KEY_APP_AUDIO_MODES, null))
        } else {
            val legacyBypassed = parseBypassedPackagesJson(prefs.getString(KEY_BYPASSED_PACKAGES, null))
            val migrated = legacyBypassed.associateWith { AppAudioMode.BYPASS }
            if (migrated.isNotEmpty() || prefs.contains(KEY_BYPASSED_PACKAGES)) {
                saveAppAudioModes(migrated)
            }
            migrated
        }
        val bypassedPackages = appAudioModes
            .filterValues { it == AppAudioMode.BYPASS }
            .keys
        val backgroundPersistenceEnabled = prefs.getBoolean(KEY_BACKGROUND_PERSISTENCE, false)
        val masterPowerOn = prefs.getBoolean(KEY_MASTER_POWER_ON, false)

        return PersistedSettings(
            presets = presets,
            selectedPresetId = selectedId,
            independentPresets = independentPresets,
            selectedIndependentPresetId = selectedIndepId,
            bypass = bypass,
            channelSwap = channelSwap,
            swapPosition = swapPos,
            deviceRules = rules,
            bypassedPackages = bypassedPackages,
            globalLowLatency = globalLowLatency,
            appAudioModes = appAudioModes,
            backgroundPersistenceEnabled = backgroundPersistenceEnabled,
            masterPowerOn = masterPowerOn
        )
    }

    fun savePresets(presets: List<EqPreset>) {
        val arr = JSONArray().apply { presets.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    fun saveSelectedPresetId(presetId: String) {
        prefs.edit().putString(KEY_SELECTED_PRESET_ID, presetId).apply()
    }

    fun saveIndependentPresets(presets: List<IndependentPeqPreset>) {
        val arr = JSONArray().apply { presets.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_INDEPENDENT_PRESETS, arr.toString()).apply()
    }

    fun saveSelectedIndependentPresetId(presetId: String?) {
        prefs.edit().apply {
            if (presetId == null) remove(KEY_SELECTED_INDEPENDENT_PRESET_ID)
            else putString(KEY_SELECTED_INDEPENDENT_PRESET_ID, presetId)
        }.apply()
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

    fun saveGlobalLowLatency(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_GLOBAL_LOW_LATENCY, enabled)
            .apply()
    }

    fun saveBackgroundPersistenceEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BACKGROUND_PERSISTENCE, enabled)
            .apply()
    }

    fun saveMasterPowerOn(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_MASTER_POWER_ON, enabled)
            .apply()
    }

    fun saveAppAudioModes(modes: Map<String, AppAudioMode>) {
        val normalized = normalizeAppAudioModes(modes)
        val bypassed = normalized.filterValues { it == AppAudioMode.BYPASS }.keys
        prefs.edit()
            .putString(KEY_APP_AUDIO_MODES, serializeAppAudioModesJson(normalized))
            .putString(KEY_BYPASSED_PACKAGES, serializeBypassedPackagesJson(bypassed))
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
        private const val KEY_INDEPENDENT_PRESETS = "independent_presets_v1"
        private const val KEY_SELECTED_INDEPENDENT_PRESET_ID = "selected_independent_preset_id_v1"
        private const val KEY_BYPASS = "bypass_v1"
        private const val KEY_CHANNEL_SWAP = "channel_swap_v1"
        private const val KEY_SWAP_POSITION = "swap_position_v1"
        private const val KEY_DEVICE_RULES = "device_rules_v1"
        private const val KEY_BYPASSED_PACKAGES = "bypassed_packages_v1"
        private const val KEY_GLOBAL_LOW_LATENCY = "global_low_latency_v1"
        private const val KEY_APP_AUDIO_MODES = "app_audio_modes_v1"
        private const val KEY_BACKGROUND_PERSISTENCE = "background_persistence_v1"
        private const val KEY_MASTER_POWER_ON = "master_power_on_v1"
        private const val KEY_REMOVED_BUILTIN_IG955_V1 = "migrated_removed_builtin_ig955_v1"
        private const val KEY_MIGRATED_SPLIT_PRESETS_V1 = "migrated_split_presets_v1"
        private const val LEGACY_IG955_PRESET_ID = "preset_ig955_11_13"

        fun parseIndependentPresets(json: String?): List<IndependentPeqPreset> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(IndependentPeqPreset.fromJson(it)) }
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        /**
         * Extracts standalone [IndependentPeqPreset] entries from legacy bundled [EqPreset]s
         * without removing the bundles from [presets], preserving per-device rules and active
         * L/R selection deterministically.
         */
        fun migrateLegacyBundles(
            presets: List<EqPreset>,
            existingIndependentPresets: List<IndependentPeqPreset>,
            deviceRules: List<DeviceRoutingRule>,
            selectedPresetId: String,
            existingSelectedIndependentId: String?,
            fallbackSwap: Boolean,
            fallbackSwapPosition: SwapPosition
        ): SplitMigrationResult {
            val indepList = existingIndependentPresets.toMutableList()
            val updatedPresets = presets.map { preset ->
                if (preset.linkedIndependentPresetId == null && preset.hasIndependentSnapshot) {
                    val b = preset.baseline
                    val extractedBaseline = if (b != null) {
                        IndependentPeqBaseline(
                            leftPreampDb = b.leftPreampDb,
                            leftBands = b.leftBands.map { it.copy() },
                            rightPreampDb = b.rightPreampDb,
                            rightBands = b.rightBands.map { it.copy() }
                        )
                    } else {
                        IndependentPeqBaseline(
                            leftPreampDb = preset.leftPreampDb,
                            leftBands = preset.leftBands.map { it.copy() },
                            rightPreampDb = preset.rightPreampDb,
                            rightBands = preset.rightBands.map { it.copy() }
                        )
                    }
                    val extracted = IndependentPeqPreset(
                        id = UUID.randomUUID().toString(),
                        name = preset.name,
                        leftPreampDb = preset.leftPreampDb,
                        leftBands = preset.leftBands.map { it.copy() },
                        rightPreampDb = preset.rightPreampDb,
                        rightBands = preset.rightBands.map { it.copy() },
                        updatedAtEpochMs = preset.updatedAtEpochMs,
                        baseline = extractedBaseline
                    )
                    indepList.add(extracted)
                    preset.copy(linkedIndependentPresetId = extracted.id)
                } else {
                    preset
                }
            }

            val selectedCommon = updatedPresets.firstOrNull { it.id == selectedPresetId }
            val nextSelectedIndep = existingSelectedIndependentId
                ?: selectedCommon?.linkedIndependentPresetId

            val updatedRules = deviceRules.map { rule ->
                if (rule.action == DeviceRuleAction.PRESET) {
                    val linkedPreset = updatedPresets.firstOrNull { it.id == rule.presetId }
                    if (linkedPreset?.linkedIndependentPresetId != null) {
                        rule.copy(
                            independentAction = DeviceIndependentRuleAction.APPLY_PRESET,
                            independentPresetId = linkedPreset.linkedIndependentPresetId
                        )
                    } else {
                        rule.copy(
                            independentAction = DeviceIndependentRuleAction.CLEAR_NONE,
                            independentPresetId = null
                        )
                    }
                } else {
                    rule
                }
            }

            return SplitMigrationResult(
                presets = updatedPresets,
                independentPresets = indepList,
                selectedIndependentPresetId = nextSelectedIndep,
                deviceRules = updatedRules,
                channelSwap = fallbackSwap,
                swapPosition = fallbackSwapPosition
            )
        }

        fun normalizeAppAudioModes(modes: Map<String, AppAudioMode>): Map<String, AppAudioMode> {
            val out = LinkedHashMap<String, AppAudioMode>()
            modes.entries
                .mapNotNull { (rawPkg, mode) ->
                    val pkg = rawPkg.trim()
                    if (pkg.isEmpty() || mode == AppAudioMode.STANDARD) null else pkg to mode
                }
                .sortedBy { it.first }
                .forEach { (pkg, mode) -> out[pkg] = mode }
            return out
        }

        fun serializeAppAudioModesJson(modes: Map<String, AppAudioMode>): String {
            val obj = JSONObject()
            normalizeAppAudioModes(modes).forEach { (pkg, mode) ->
                obj.put(pkg, mode.name)
            }
            return obj.toString()
        }

        fun parseAppAudioModesJson(json: String?): Map<String, AppAudioMode> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(json)
                val out = LinkedHashMap<String, AppAudioMode>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val pkg = rawKey.trim()
                    if (pkg.isEmpty()) continue
                    val mode = runCatching { AppAudioMode.valueOf(obj.optString(rawKey)) }
                        .getOrDefault(AppAudioMode.STANDARD)
                    if (mode != AppAudioMode.STANDARD) {
                        out[pkg] = mode
                    }
                }
                normalizeAppAudioModes(out)
            } catch (_: Throwable) {
                emptyMap()
            }
        }

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
