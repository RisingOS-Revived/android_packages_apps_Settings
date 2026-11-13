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

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * A controller that manages the information about battery health, derived from the battery's
 * maximum capacity relative to its design capacity.
 */
public class BatteryHealthPreferenceController extends BasePreferenceController {

    private static final int REPLACEMENT_THRESHOLD_PERCENT = 75;

    public BatteryHealthPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        final Integer percentage =
                BatteryMaximumCapacityPreferenceController.getCapacityPercentage(mContext);
        if (percentage == null) {
            return mContext.getString(R.string.battery_maximum_capacity_not_available);
        }
        return mContext.getString(percentage < REPLACEMENT_THRESHOLD_PERCENT
                ? R.string.battery_health_needs_replacement
                : R.string.battery_health_normal);
    }
}
