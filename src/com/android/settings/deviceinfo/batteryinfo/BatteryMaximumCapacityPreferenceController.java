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
import android.os.BatteryManager;

import com.android.internal.os.PowerProfile;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.fuelgauge.BatteryUtils;

/**
 * A controller that manages the information about battery maximum capacity.
 */
public class BatteryMaximumCapacityPreferenceController extends BasePreferenceController {

    public BatteryMaximumCapacityPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        boolean isFeatureEnabled = mContext.getResources().getBoolean(R.bool.config_show_battery_maximum_capacity);
        return isFeatureEnabled ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        final int[] capacity = getCapacityMah(mContext);
        if (capacity != null) {
            int percentage = (capacity[0] * 100) / capacity[1];
            return mContext.getString(
                    R.string.battery_maximum_capacity_summary, capacity[0], percentage);
        }

        return mContext.getString(R.string.battery_maximum_capacity_not_available);
    }

    static int[] getCapacityMah(Context context) {
        Intent batteryIntent = BatteryUtils.getBatteryIntent(context);
        final int maxCapacityUah =
                batteryIntent.getIntExtra(BatteryManager.EXTRA_MAXIMUM_CAPACITY, -1);
        int designCapacityUah =
                batteryIntent.getIntExtra(BatteryManager.EXTRA_DESIGN_CAPACITY, -1);
        if (designCapacityUah <= 0) {
            final PowerProfile profile = new PowerProfile(context);
            designCapacityUah = (int) profile.getBatteryCapacity() * 1000;
        }

        if (maxCapacityUah > 0 && designCapacityUah > 0) {
            return new int[] {maxCapacityUah / 1_000, designCapacityUah / 1_000};
        }
        return null;
    }

    static Integer getCapacityPercentage(Context context) {
        final int[] capacity = getCapacityMah(context);
        return capacity == null ? null : (capacity[0] * 100) / capacity[1];
    }
}
