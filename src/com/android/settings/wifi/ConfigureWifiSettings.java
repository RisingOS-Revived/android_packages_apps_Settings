/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.settings.wifi;

import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.net.MacAddress;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import android.util.EventLog;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.wifi.p2p.WifiP2pPreferenceController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

// LINT.IfChange
@SearchIndexable
public class ConfigureWifiSettings extends DashboardFragment {

    private static final String TAG = "ConfigureWifiSettings";
    @VisibleForTesting
    static final String KEY_INSTALL_CREDENTIALS = "install_credentials";
    private static final String ACTION_INSTALL_CERTS = "android.credentials.INSTALL";
    private static final String PACKAGE_INSTALL_CERTS = "com.android.certinstaller";
    private static final String CLASS_INSTALL_CERTS = "com.android.certinstaller.CertInstallerMain";
    private static final String KEY_INSTALL_CERTIFICATE = "certificate_install_usage";
    private static final String INSTALL_CERTIFICATE_VALUE = "wifi";

    public static final int WIFI_WAKEUP_REQUEST_CODE = 600;

    private WifiWakeupPreferenceController mWifiWakeupPreferenceController;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (isGuestUser(context)) return;

        if (!isCatalystEnabled()) {
            mWifiWakeupPreferenceController = use(WifiWakeupPreferenceController.class);
            mWifiWakeupPreferenceController.setFragment(this);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (isGuestUser(getContext())) return;

        final Preference installCredentialsPref = findPreference(KEY_INSTALL_CREDENTIALS);
        if (installCredentialsPref != null) {
            installCredentialsPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(ACTION_INSTALL_CERTS);
                intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setComponent(
                        new ComponentName(PACKAGE_INSTALL_CERTS, CLASS_INSTALL_CERTS));
                intent.putExtra(KEY_INSTALL_CERTIFICATE, INSTALL_CERTIFICATE_VALUE);
                getContext().startActivity(intent);
                return true;
            });
        } else {
            Log.d(TAG, "Can not find the preference.");
        }
        setupCustomMacPreference();
    }

    private static final String KEY_CUSTOM_WIFI_MAC = "custom_wifi_mac";

    private void setupCustomMacPreference() {
        final EditTextPreference pref = findPreference(KEY_CUSTOM_WIFI_MAC);
        if (pref == null) return;
        final Context context = getContext();
        final String current = Settings.Global.getString(
                context.getContentResolver(), KEY_CUSTOM_WIFI_MAC);
        pref.setText(current);
        pref.setSummary(TextUtils.isEmpty(current) ? "Off" : current);
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            final String value = newValue == null ? "" : ((String) newValue).trim();
            if (!value.isEmpty()) {
                try {
                    final byte[] b = MacAddress.fromString(value).toByteArray();
                    final boolean isMulticast = (b[0] & 0x01) != 0; // also covers broadcast
                    boolean isAllZero = true;
                    for (byte x : b) {
                        if (x != 0) isAllZero = false;
                    }
                    final boolean isReserved = b[0] == 2 && b[1] == 0 && b[2] == 0
                            && b[3] == 0 && b[4] == 0 && b[5] == 0; // 02:00:00:00:00:00
                    if (isMulticast || isAllZero || isReserved) {
                        throw new IllegalArgumentException("not a usable unicast MAC");
                    }
                } catch (IllegalArgumentException e) {
                    Toast.makeText(context, "Invalid MAC address", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            Settings.Global.putString(context.getContentResolver(), KEY_CUSTOM_WIFI_MAC,
                    value.isEmpty() ? null : value);
            pref.setSummary(value.isEmpty() ? "Off" : value);
            return true;
        });
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!isGuestUser(getContext())) return;

        Log.w(TAG, "Displays the restricted UI because the user is a guest.");
        EventLog.writeEvent(0x534e4554, "231987122", -1 /* UID */, "User is a guest");

        // Restricted UI
        final TextView emptyView = getActivity().findViewById(android.R.id.empty);
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.wifi_empty_list_user_restricted);
        }
        getPreferenceScreen().removeAll();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.CONFIGURE_WIFI;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_configure_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        if (isGuestUser(context)) return null;

        final WifiManager wifiManager = getSystemService(WifiManager.class);
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new WifiP2pPreferenceController(context, getSettingsLifecycle(),
                wifiManager));
        return controllers;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mWifiWakeupPreferenceController != null && requestCode == WIFI_WAKEUP_REQUEST_CODE) {
            mWifiWakeupPreferenceController.onActivityResult(requestCode, resultCode);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.wifi_configure_settings) {
                protected boolean isPageSearchEnabled(Context context) {
                    if (isGuestUser(context)) return false;
                    return context.getResources()
                            .getBoolean(R.bool.config_show_wifi_settings);
                }
            };

    /** @return  true if the current user is the guest. */
    public static boolean isGuestUser(Context context) {
        if (context == null) return false;
        final UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager == null) return false;
        return userManager.isGuestUser();
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return ConfigureWifiScreen.KEY;
    }
}
// LINT.ThenChange(ConfigureWifiScreen.kt, ConfigureWifiApiScreen.kt)
