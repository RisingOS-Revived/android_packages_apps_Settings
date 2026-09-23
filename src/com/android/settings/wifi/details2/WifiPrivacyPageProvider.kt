/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.wifi.details2

import android.content.Context
import android.net.MacAddress
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SimpleClock
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.android.settings.R
import com.android.settings.overlay.FeatureFactory.Companion.featureFactory
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.preference.Radio2
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.Category
import com.android.settingslib.spa.widget.ui.SettingsListItem
import com.android.wifitrackerlib.WifiEntry
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64

const val WIFI_ENTRY_KEY = "wifiEntryKey"

object WifiPrivacyPageProvider : SettingsPageProvider {
    override val name = "WifiPrivacy"
    const val TAG = "WifiPrivacyPageProvider"

    override val parameter = listOf(
        navArgument(WIFI_ENTRY_KEY) { type = NavType.StringType },
    )

    @Composable
    override fun Page(arguments: Bundle?) {
        val wifiEntryKey =
            String(Base64.getUrlDecoder().decode(arguments!!.getString(WIFI_ENTRY_KEY)))
        if (wifiEntryKey != null) {
            val context = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val wifiEntry = remember {
                getWifiEntry(context, wifiEntryKey, lifecycle)
            }
            WifiPrivacyPage(wifiEntry)
        }
    }

    fun getRoute(
        wifiEntryKey: String,
    ): String = "${name}/${Base64.getUrlEncoder().encodeToString(wifiEntryKey.toByteArray())}"
}

@Composable
fun WifiPrivacyPage(wifiEntry: WifiEntry) {
    val isSelectable: Boolean = wifiEntry.canSetPrivacy()
    RegularScaffold(
        title = stringResource(id = R.string.wifi_privacy_settings)
    ) {
        Column {
            wifiEntry.wifiConfiguration?.let {
                DeviceNameSwitchPreference(wifiEntry)
            }

            MacOptionsSection(wifiEntry, isSelectable)
        }
    }
}

private const val CUSTOM_MAC_PER_NETWORK_SETTING = "custom_wifi_mac_per_network"

private fun readCustomMacMap(context: Context): LinkedHashMap<String, String> {
    val map = LinkedHashMap<String, String>()
    val raw = Settings.Global.getString(context.contentResolver, CUSTOM_MAC_PER_NETWORK_SETTING)
    raw?.lines()?.forEach { line ->
        val eq = line.lastIndexOf('=')
        if (eq > 0) map[line.substring(0, eq)] = line.substring(eq + 1)
    }
    return map
}

private fun writeCustomMacMap(context: Context, map: Map<String, String>) {
    Settings.Global.putString(
        context.contentResolver,
        CUSTOM_MAC_PER_NETWORK_SETTING,
        map.entries.joinToString("\n") { "${it.key}=${it.value}" }
    )
}

private const val CUSTOM_MAC_OPTION_ID = 1000

@Composable
fun MacOptionsSection(wifiEntry: WifiEntry, isSelectable: Boolean) {
    val context = LocalContext.current
    val ssidKey = wifiEntry.wifiConfiguration?.SSID

    val title = stringResource(id = R.string.wifi_privacy_mac_settings)
    val wifiPrivacyEntries = stringArrayResource(R.array.wifi_privacy_entries_ext)
    val wifiPrivacyValues = stringArrayResource(R.array.wifi_privacy_values_ext)
    val customLabel = stringResource(R.string.wifi_custom_mac_title)

    val savedCustom = remember { ssidKey?.let { readCustomMacMap(context)[it] } ?: "" }
    var customText by remember { mutableStateOf(savedCustom) }
    val textsSelectedId = rememberSaveable {
        mutableIntStateOf(
            if (savedCustom.isNotEmpty()) CUSTOM_MAC_OPTION_ID else wifiEntry.privacy
        )
    }
    val dataList = remember {
        val stock = wifiPrivacyEntries.mapIndexed { index, text ->
            ListPreferenceOption(id = wifiPrivacyValues[index].toInt(), text = text)
        }
        if (ssidKey != null) {
            stock + ListPreferenceOption(id = CUSTOM_MAC_OPTION_ID, text = customLabel)
        } else {
            stock
        }
    }

    val onIdSelected: (Int) -> Unit = { id ->
        textsSelectedId.intValue = id
        if (id == CUSTOM_MAC_OPTION_ID) {
            if (ssidKey != null && customText.isNotEmpty() && isValidCustomMac(customText)) {
                val map = readCustomMacMap(context)
                map[ssidKey] = customText.trim()
                writeCustomMacMap(context, map)
            }
        } else {
            if (ssidKey != null) {
                val map = readCustomMacMap(context)
                if (map.remove(ssidKey) != null) writeCustomMacMap(context, map)
            }
            customText = ""
            onSelectedChange(wifiEntry, id)
        }
    }

    Category(modifier = Modifier.selectableGroup(), title = title) {
        for (option in dataList) {
            if (option.id == CUSTOM_MAC_OPTION_ID && ssidKey != null) {
                val customSelected = textsSelectedId.intValue == CUSTOM_MAC_OPTION_ID
                val isValid = customText.isEmpty() || isValidCustomMac(customText)
                CustomMacOptionRow(
                    text = option.text,
                    selected = customSelected,
                    enabled = isSelectable,
                    onSelect = { onIdSelected(CUSTOM_MAC_OPTION_ID) },
                ) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { newText ->
                            customText = newText
                            if (isValidCustomMac(newText)) {
                                val map = readCustomMacMap(context)
                                map[ssidKey] = newText.trim()
                                writeCustomMacMap(context, map)
                            }
                        },
                        enabled = isSelectable && customSelected,
                        placeholder = { Text("02:11:22:33:44:55") },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (isValid) R.string.wifi_custom_mac_supporting
                                    else R.string.wifi_custom_mac_error
                                )
                            )
                        },
                        isError = customSelected && !isValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Radio2(option, textsSelectedId.intValue, isSelectable, onIdSelected)
            }
        }
    }
}

@Composable
private fun CustomMacOptionRow(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    field: @Composable () -> Unit,
) {
    val surfaceBright = MaterialTheme.colorScheme.surfaceBright
    val gap = if (isSpaExpressiveEnabled) SettingsSpace.extraSmall6
    else SettingsDimension.itemDividerHeight
    val radioWidth = 24.dp
    val rowPadding = if (isSpaExpressiveEnabled) SettingsDimension.itemPadding
    else SettingsDimension.dialogItemPadding

    Column(
        modifier = Modifier.fillMaxWidth().let {
            if (isSpaExpressiveEnabled) it.background(surfaceBright) else it
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .let {
                    if (isSpaExpressiveEnabled) {
                        it.heightIn(min = SettingsDimension.preferenceMinHeight)
                    } else it
                }
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    onClick = onSelect,
                    role = Role.RadioButton,
                )
                .padding(rowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(modifier = Modifier.width(gap))
            SettingsListItem(text = text, enabled = enabled)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = SettingsDimension.itemPaddingStart + radioWidth + gap,
                end = SettingsDimension.itemPaddingEnd,
                bottom = SettingsDimension.itemPaddingVertical,
            )
        ) {
            field()
        }
    }
}

private fun isValidCustomMac(text: String): Boolean = try {
    val bytes = MacAddress.fromString(text.trim()).toByteArray()
    val isMulticast = (bytes[0].toInt() and 0x01) != 0 // also covers broadcast
    val isAllZero = bytes.all { it.toInt() == 0 }
    val isReserved = bytes.contentEquals(byteArrayOf(2, 0, 0, 0, 0, 0)) // 02:00:00:00:00:00
    !isMulticast && !isAllZero && !isReserved
} catch (e: IllegalArgumentException) {
    false
}

@Composable
fun DeviceNameSwitchPreference(wifiEntry: WifiEntry) {
    val title = stringResource(id = R.string.wifi_privacy_device_name_settings)
    Category(title = title) {
        var checked by remember {
            mutableStateOf(wifiEntry.wifiConfiguration?.isSendDhcpHostnameEnabled)
        }
        val context = LocalContext.current
        val wifiManager = context.getSystemService(WifiManager::class.java)!!
        SwitchPreference(object : SwitchPreferenceModel {
            override val title =
                context.resources.getString(
                    R.string.wifi_privacy_send_device_name_toggle_title
                )
            override val summary =
                {
                    context.resources.getString(
                        R.string.wifi_privacy_send_device_name_toggle_summary
                    )
                }
            override val checked = { checked }
            override val onCheckedChange: (Boolean) -> Unit = { newChecked ->
                wifiEntry.wifiConfiguration?.let {
                    it.isSendDhcpHostnameEnabled = newChecked
                    wifiManager.save(it, null /* listener */)
                    checked = newChecked
                }
            }
        })
    }
}

fun onSelectedChange(wifiEntry: WifiEntry, privacy: Int) {
    if (wifiEntry.privacy == privacy) {
        // Prevent disconnection + reconnection if settings not changed.
        return
    }
    wifiEntry.setPrivacy(privacy)

    // To activate changing, we need to reconnect network. WiFi will auto connect to
    // current network after disconnect(). Only needed when this is connected network.

    // To activate changing, we need to reconnect network. WiFi will auto connect to
    // current network after disconnect(). Only needed when this is connected network.
    if (wifiEntry.getConnectedState() == WifiEntry.CONNECTED_STATE_CONNECTED) {
        wifiEntry.disconnect(null /* callback */)
        wifiEntry.connect(null /* callback */)
    }
}

fun getWifiEntry(
    context: Context,
    wifiEntryKey: String,
    liftCycle: androidx.lifecycle.Lifecycle
): WifiEntry {
    // Max age of tracked WifiEntries
    val MAX_SCAN_AGE_MILLIS: Long = 15000
    // Interval between initiating SavedNetworkTracker scans
    val SCAN_INTERVAL_MILLIS: Long = 10000
    val mWorkerThread = HandlerThread(
        WifiPrivacyPageProvider.TAG,
        Process.THREAD_PRIORITY_BACKGROUND
    )
    mWorkerThread.start()
    val elapsedRealtimeClock: Clock = object : SimpleClock(ZoneOffset.UTC) {
        override fun millis(): Long {
            return android.os.SystemClock.elapsedRealtime()
        }
    }
    val mNetworkDetailsTracker = featureFactory
        .wifiTrackerLibProvider
        .createNetworkDetailsTracker(
            liftCycle,
            context,
            Handler(Looper.getMainLooper()),
            mWorkerThread.getThreadHandler(),
            elapsedRealtimeClock,
            MAX_SCAN_AGE_MILLIS,
            SCAN_INTERVAL_MILLIS,
            wifiEntryKey
        )
    return mNetworkDetailsTracker.wifiEntry
}
