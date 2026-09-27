/*
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

package com.android.settings.password;

import static android.app.admin.DevicePolicyResources.Strings.Settings.CONFIRM_WORK_PROFILE_PIN_HEADER;
import static android.app.admin.DevicePolicyResources.Strings.Settings.WORK_PROFILE_LAST_PIN_ATTEMPT_BEFORE_WIPE;
import static android.app.admin.DevicePolicyResources.UNDEFINED;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.VerifyCredentialResponse;
import com.android.settings.R;
import com.google.android.setupdesign.util.ThemeHelper;

import java.time.Duration;

public class ConfirmLockKnockCode extends ConfirmDeviceCredentialBaseActivity {

    public static class InternalActivity extends ConfirmLockKnockCode {
    }

    private enum Stage {
        NeedToUnlock,
        NeedToUnlockWrong,
        LockedOut
    }

    @Override
    public Intent getIntent() {
        final Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ConfirmLockKnockCodeFragment.class.getName());
        modIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_USE_EXPRESSIVE_STYLE,
                ThemeHelper.shouldApplyGlifExpressiveStyle(getApplicationContext()));
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return ConfirmLockKnockCodeFragment.class.getName().equals(fragmentName);
    }

    public static class ConfirmLockKnockCodeFragment extends ConfirmDeviceCredentialBaseFragment
            implements CredentialCheckResultTracker.Listener {

        private static final String FRAGMENT_TAG_CHECK_LOCK_RESULT = "check_lock_result";

        @Nullable private KnockCodePadView mPad;
        @Nullable private AsyncTask<?, ?, ?> mPendingLockCheck;
        private CredentialCheckResultTracker mCredentialCheckResultTracker;
        @Nullable private CountDownTimer mCountdownTimer;
        private boolean mDisappearing;

        private Stage mUiStage = Stage.NeedToUnlock;

        private CharSequence mHeaderText;
        private CharSequence mDetailsText;

        private boolean mIsManagedProfile;

        public ConfirmLockKnockCodeFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.confirm_lock_knock_code, container, false);
            mGlifLayout = view.findViewById(R.id.setup_wizard_layout);
            mPad = view.findViewById(R.id.knock_code_pad);
            mErrorTextView = (TextView) view.findViewById(R.id.errorText);

            mIsManagedProfile = UserManager.get(getActivity()).isManagedProfile(mEffectiveUserId);
            mCredentialCheckResultTracker = (CredentialCheckResultTracker) getFragmentManager()
                    .findFragmentByTag(FRAGMENT_TAG_CHECK_LOCK_RESULT);
            if (mCredentialCheckResultTracker == null) {
                mCredentialCheckResultTracker = new CredentialCheckResultTracker();
                getFragmentManager().beginTransaction().add(mCredentialCheckResultTracker,
                        FRAGMENT_TAG_CHECK_LOCK_RESULT).commit();
            }

            mPad.setGridSize(mLockPatternUtils.getKnockCodeGridSize(mEffectiveUserId));
            mPad.setExpectedLength(mLockPatternUtils.getKnockCodeLength(mEffectiveUserId));
            mPad.setOnKnockCompleteListener(this::verifyKnockCode);

            final Intent intent = getActivity().getIntent();
            if (intent != null) {
                mHeaderText = intent.getCharSequenceExtra(
                        ConfirmDeviceCredentialBaseFragment.HEADER_TEXT);
                mDetailsText = intent.getCharSequenceExtra(
                        ConfirmDeviceCredentialBaseFragment.DETAILS_TEXT);
            }

            updateStage(Stage.NeedToUnlock);
            setAccessibilityTitle(mGlifLayout.getHeaderText());

            if (savedInstanceState == null && !mFrp && !mRepairMode && !mRemoteValidation
                    && !mLockPatternUtils.isKnockCodeEnabled(mEffectiveUserId)) {
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
            }

            return view;
        }

        @Override
        public void onResume() {
            super.onResume();

            final long deadline = mLockPatternUtils.getLockoutEndTime(mEffectiveUserId).toMillis();
            if (deadline != 0) {
                mCredentialCheckResultTracker.clearResult();
                handleAttemptLockout(deadline);
            } else if (mPad != null && !mPad.isEnabled()) {
                updateStage(Stage.NeedToUnlock);
            }
            mCredentialCheckResultTracker.setListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();

            if (mCountdownTimer != null) {
                mCountdownTimer.cancel();
            }
            mCredentialCheckResultTracker.setListener(null);
        }

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.CONFIRM_LOCK_PASSWORD;
        }

        @Override
        protected void onShowError() {
        }

        private void updateStage(Stage stage) {
            mUiStage = stage;
            switch (stage) {
                case NeedToUnlock:
                    mGlifLayout.setHeaderText(mHeaderText != null ? mHeaderText : getDefaultHeader());

                    if (mIsManagedProfile) {
                        mGlifLayout.getDescriptionTextView().setVisibility(View.GONE);
                    } else {
                        mGlifLayout.setDescriptionText(
                                mDetailsText == null ? getDefaultDetails() : mDetailsText);
                    }

                    mErrorTextView.setText("");
                    updateErrorMessage(
                            mLockPatternUtils.getCurrentFailedPasswordAttempts(mEffectiveUserId));

                    setPadEnabled(true);
                    mPad.clearSequence();
                    break;
                case NeedToUnlockWrong:
                    showError(R.string.knock_code_wrong, CLEAR_WRONG_ATTEMPT_TIMEOUT_MS);

                    setPadEnabled(true);
                    mPad.clearSequence();
                    break;
                case LockedOut:
                    mPad.clearSequence();
                    setPadEnabled(false);
                    break;
            }

            mGlifLayout.getHeaderTextView().announceForAccessibility(mGlifLayout.getHeaderText());
        }

        private void setPadEnabled(boolean enabled) {
            mPad.setEnabled(enabled);
            mPad.setAlpha(enabled ? 1f : 0.3f);
        }

        private String getDefaultHeader() {
            if (mIsManagedProfile) {
                return mDevicePolicyManager.getResources().getString(
                        CONFIRM_WORK_PROFILE_PIN_HEADER,
                        () -> getString(R.string.lockpassword_confirm_your_work_pin_header));
            }
            return getString(R.string.knock_code_confirm_existing_header);
        }

        private String getDefaultDetails() {
            if (mRepairMode) {
                return getString(R.string.lockpassword_confirm_repair_mode_pin_details);
            }
            return isStrongAuthRequired()
                    ? getString(R.string.lockpassword_strong_auth_required_device_pin)
                    : getString(R.string.knock_code_confirm_existing_details);
        }

        private void verifyKnockCode(String sequence) {
            if (mUiStage == Stage.LockedOut || mPendingLockCheck != null || mDisappearing) {
                return;
            }

            mPad.setEnabled(false);

            final LockscreenCredential credential = LockscreenCredential.createPin(sequence);
            final Intent intent = new Intent();

            if (mRemoteValidation) {
                mCredentialCheckResultTracker.setResult(false, intent, Duration.ZERO,
                        mEffectiveUserId);
                return;
            }

            if (mReturnGatekeeperPassword) {
                if (isInternalActivity()) {
                    startVerifyKnockCode(credential, intent,
                            LockPatternUtils.VERIFY_FLAG_REQUEST_GK_PW_HANDLE);
                    return;
                }
            } else if (mForceVerifyPath) {
                if (isInternalActivity()) {
                    final int flags = mRequestWriteRepairModePassword
                            ? LockPatternUtils.VERIFY_FLAG_WRITE_REPAIR_MODE_PW : 0;
                    startVerifyKnockCode(credential, intent, flags);
                    return;
                }
            } else {
                startCheckKnockCode(credential, intent);
                return;
            }

            mCredentialCheckResultTracker.setResult(false, intent, Duration.ZERO,
                    mEffectiveUserId);
        }

        private boolean isInternalActivity() {
            return getActivity() instanceof ConfirmLockKnockCode.InternalActivity;
        }

        private void startVerifyKnockCode(LockscreenCredential credential, final Intent intent,
                @LockPatternUtils.VerifyFlag int flags) {
            final int localEffectiveUserId = mEffectiveUserId;
            final int localUserId = mUserId;
            final LockPatternChecker.OnVerifyCallback onVerifyCallback = response -> {
                final Duration timeout = response.getTimeout();
                mPendingLockCheck = null;
                final boolean matched = response.isMatched();
                if (matched && mReturnCredentials) {
                    if ((flags & LockPatternUtils.VERIFY_FLAG_REQUEST_GK_PW_HANDLE) != 0) {
                        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_GK_PW_HANDLE,
                                response.getGatekeeperPasswordHandle());
                    } else {
                        intent.putExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN,
                                response.getGatekeeperHAT());
                    }
                }
                mCredentialCheckResultTracker.setResult(matched, intent, timeout,
                        localEffectiveUserId);
            };
            mPendingLockCheck = (localEffectiveUserId == localUserId)
                    ? LockPatternChecker.verifyCredential(mLockPatternUtils, credential, localUserId,
                            flags, onVerifyCallback)
                    : LockPatternChecker.verifyTiedProfileChallenge(mLockPatternUtils, credential,
                            localUserId, flags, onVerifyCallback);
        }

        private void startCheckKnockCode(final LockscreenCredential credential,
                final Intent intent) {
            final int localEffectiveUserId = mEffectiveUserId;
            mPendingLockCheck = LockPatternChecker.checkCredential(
                    mLockPatternUtils,
                    credential,
                    localEffectiveUserId,
                    new LockPatternChecker.OnCheckCallback() {
                        @Override
                        public void onChecked(VerifyCredentialResponse response) {
                            final boolean matched = response.isMatched();
                            final Duration timeout = response.getTimeout();
                            mPendingLockCheck = null;
                            if (matched && isInternalActivity() && mReturnCredentials) {
                                intent.putExtra(
                                        ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, credential);
                            }
                            mCredentialCheckResultTracker.setResult(matched, intent, timeout,
                                    localEffectiveUserId);
                        }
                    });
        }

        private void onKnockCodeChecked(boolean matched, Intent intent, Duration timeout,
                int effectiveUserId, boolean newResult) {
            if (matched) {
                if (newResult) {
                    ConfirmDeviceCredentialUtils.reportSuccessfulAttempt(mLockPatternUtils,
                            mUserManager, mDevicePolicyManager, mEffectiveUserId,
                            /* isStrongAuth */ true);
                }
                startDisappearAnimation(intent);
                ConfirmDeviceCredentialUtils.checkForPendingIntent(getActivity());
            } else {
                if (timeout.isPositive()) {
                    refreshLockScreen();
                    final long deadline = mLockPatternUtils.getLockoutEndTime(
                            effectiveUserId).toMillis();
                    handleAttemptLockout(deadline);
                } else {
                    updateStage(Stage.NeedToUnlockWrong);
                }
                if (newResult) {
                    reportFailedAttempt();
                }
            }
        }

        private void startDisappearAnimation(final Intent intent) {
            if (mDisappearing) {
                return;
            }
            mDisappearing = true;

            final ConfirmLockKnockCode activity = (ConfirmLockKnockCode) getActivity();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
        }

        private void handleAttemptLockout(long elapsedRealtimeDeadline) {
            clearResetErrorRunnable();
            updateStage(Stage.LockedOut);
            final long elapsedRealtime = SystemClock.elapsedRealtime();
            mCountdownTimer = new CountDownTimer(
                    elapsedRealtimeDeadline - elapsedRealtime,
                    LockPatternUtils.FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS) {

                @Override
                public void onTick(long millisUntilFinished) {
                    final int secondsCountdown = (int) (millisUntilFinished / 1000);
                    mErrorTextView.setText(getString(
                            R.string.knock_code_too_many_failed_attempts, secondsCountdown));
                }

                @Override
                public void onFinish() {
                    updateStage(Stage.NeedToUnlock);
                }
            }.start();
        }

        @Override
        public void onCredentialChecked(boolean matched, Intent intent, Duration timeout,
                int effectiveUserId, boolean newResult) {
            onKnockCodeChecked(matched, intent, timeout, effectiveUserId, newResult);
        }

        @Override
        protected String getLastTryOverrideErrorMessageId(int userType) {
            if (userType == USER_TYPE_MANAGED_PROFILE) {
                return WORK_PROFILE_LAST_PIN_ATTEMPT_BEFORE_WIPE;
            }

            return UNDEFINED;
        }

        @Override
        protected int getLastTryDefaultErrorMessage(int userType) {
            switch (userType) {
                case USER_TYPE_PRIMARY:
                    return R.string.lock_last_pin_attempt_before_wipe_device;
                case USER_TYPE_MANAGED_PROFILE:
                    return R.string.lock_last_pin_attempt_before_wipe_profile;
                case USER_TYPE_SECONDARY:
                    return R.string.lock_last_pin_attempt_before_wipe_user;
                default:
                    throw new IllegalArgumentException("Unrecognized user type:" + userType);
            }
        }

        @Override
        protected void authenticationSucceeded() {
            mCredentialCheckResultTracker.setResult(true, new Intent(),
                    Duration.ZERO, mEffectiveUserId);
        }
    }
}
