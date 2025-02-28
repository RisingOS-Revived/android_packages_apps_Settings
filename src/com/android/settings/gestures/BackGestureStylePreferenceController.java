/*
 * Copyright (c) 2025 Rising Revived Android Project
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
package com.android.settings.gestures;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import com.android.settings.core.TogglePreferenceController;
import com.android.settings.R;

/**
 * Controller for the back gesture style toggle
 */
public class BackGestureStylePreferenceController extends TogglePreferenceController {
    private static final String TAG = "BackGestureStylePrefCtrl";
    private static final String BACK_GESTURE_STYLE_CLASSIC = "back_gesture_style_classic";

    public BackGestureStylePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(mContext.getContentResolver(), 
                BACK_GESTURE_STYLE_CLASSIC, 0) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.System.putInt(mContext.getContentResolver(),
                BACK_GESTURE_STYLE_CLASSIC, isChecked ? 1 : 0);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(R.string.back_gesture_style_classic_summary);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_system;
    }
}
