/*
 * Copyright (C) 2024 Paranoid Android
 * Copyright (C) 2026 RisingOS (revived) Android Project
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

package com.android.settings.deviceinfo.batteryinfo;

import android.content.Context;
import android.content.Intent;
import android.icu.text.MeasureFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.os.BatteryManager;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.fuelgauge.BatteryUtils;

import java.util.Locale;

/**
 * A controller that manages the information about battery voltage.
 */
public class BatteryVoltagePreferenceController extends BasePreferenceController {

    private static final int UNDER_VOLTAGE_THRESHOLD_MV = 3300;
    private static final int OVER_VOLTAGE_THRESHOLD_MV = 4500;

    public BatteryVoltagePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        final Intent batteryIntent = BatteryUtils.getBatteryIntent(mContext);
        final int voltageMillivolts = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

        if (voltageMillivolts > 0) {
            float voltage = voltageMillivolts / 1_000f;

            final String value = MeasureFormat.getInstance(
                            Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
                    .format(new Measure(voltage, MeasureUnit.VOLT));
            final int statusRes;
            if (voltageMillivolts < UNDER_VOLTAGE_THRESHOLD_MV) {
                statusRes = R.string.battery_voltage_under;
            } else if (voltageMillivolts > OVER_VOLTAGE_THRESHOLD_MV) {
                statusRes = R.string.battery_voltage_over;
            } else {
                statusRes = R.string.battery_voltage_normal;
            }
            return mContext.getString(R.string.battery_voltage_summary,
                    mContext.getString(statusRes), value);
        }

        return mContext.getText(R.string.battery_voltage_not_available);
    }
}
