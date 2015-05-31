/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import java.util.HashMap;

public class AirplaneModeEnabler implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AirplaneModeEnabler";

    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;

    private final TelephonyManager mTelephonyManager;

    private final SwitchPreference mSwitchPref;

    private static final int EVENT_CHECK_AIRPLANE_CHANGE_COMPLETED = 4;
    private static final int EVENT_WAITING_AIRPLANE_MODE_CHANGED_TIMEOUT = 5;

    private static final long CHECK_AIRPLANE_MODE_CHANGE_DELAYED = 30*1000;

    private static final int DEFAULT_PHONE_ID = 0;

    private HashMap<Integer, Integer> mServiceStates = new HashMap<Integer, Integer>();

    private boolean mIsAirplaneModeChanging = false;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_WAITING_AIRPLANE_MODE_CHANGED_TIMEOUT:
                    Log.w(TAG, "EVENT_WAITING_AIRPLANE_MODE_CHANGED_TIMEOUT");
                    onAirplaneModeChanged();
                    break;
                case EVENT_CHECK_AIRPLANE_CHANGE_COMPLETED:
                    if (mIsAirplaneModeChanging && isAirplaneModeChangeCompleted()) {
                        onAirplaneModeChanged();
                    }
                    break;
            }
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (DBG) Log.d(TAG, "mAirplaneModeObserver changed, isAirplaneMode on:"
                + isAirplaneModeOn(mContext) + ", mIsAirplaneModeChanging:" + mIsAirplaneModeChanging);
            if(!mIsAirplaneModeChanging) {
                onAirplaneModeChanged();
            } else {
                checkIfAirplaneModeChangeCompleted();
            }
        }
    };

    private final IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
               if (DBG) Log.d(TAG, "receive ACTION_SERVICE_STATE_CHANGED");
               final int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                    SubscriptionManager.INVALID_SIM_SLOT_INDEX);
               if (SubscriptionManager.isValidPhoneId(phoneId)) {
                   ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
                   final int newState = serviceState.getState();
                   if (DBG) Log.d(TAG, "SERVICE_STATE_CHANGED, phoneId=" + phoneId + ", newState=" + newState);
                   mServiceStates.put(phoneId, newState);
                   checkIfAirplaneModeChangeCompleted();
               }
            }
        }
    };

    public AirplaneModeEnabler(Context context, SwitchPreference airplaneModeSwitchPreference) {
        
        mContext = context;
        mSwitchPref = airplaneModeSwitchPreference;

        airplaneModeSwitchPreference.setPersistent(false);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
    }

    public void resume() {
        mIsAirplaneModeChanging = false;
        mSwitchPref.setChecked(isAirplaneModeOn(mContext));
        mSwitchPref.setEnabled(true);
        mSwitchPref.setOnPreferenceChangeListener(this);
        mContext.registerReceiver(mReceiver, mIntentFilter);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
    }
    
    public void pause() {
        mHandler.removeMessages(EVENT_WAITING_AIRPLANE_MODE_CHANGED_TIMEOUT);
        mSwitchPref.setOnPreferenceChangeListener(null);
        mContext.unregisterReceiver(mReceiver);
        mContext.getContentResolver().unregisterContentObserver(mAirplaneModeObserver);
    }

    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void setAirplaneModeOn(boolean enabling) {
        if (DBG) Log.d(TAG, "setAirplaneModeOn to " + enabling);
        mSwitchPref.setEnabled(false);
        mIsAirplaneModeChanging = true;
        // Change the system setting
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 
                                enabling ? 1 : 0);
        // Update the UI to reflect system setting
        mSwitchPref.setChecked(enabling);
        // Post the intent
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enabling);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        // Send a message to finish the changing if timeout
        mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_WAITING_AIRPLANE_MODE_CHANGED_TIMEOUT),
                                    CHECK_AIRPLANE_MODE_CHANGE_DELAYED);
    }

    /**
     * Called when we've received confirmation that the airplane mode was set.
     * TODO: We update the checkbox summary when we get notified
     * that mobile radio is powered up/down. We should not have dependency
     * on one radio alone. We need to do the following:
     * - handle the case of wifi/bluetooth failures
     * - mobile does not send failure notification, fail on timeout.
     */
    private void onAirplaneModeChanged() {
        mHandler.removeMessages(EVENT_WAITING_AIRPLANE_MODE_CHANGED_TIMEOUT);
        mIsAirplaneModeChanging = false;
        mServiceStates.clear();
        mSwitchPref.setChecked(isAirplaneModeOn(mContext));
        mSwitchPref.setEnabled(true);
    }

    private void checkIfAirplaneModeChangeCompleted() {
       mHandler.removeMessages(EVENT_CHECK_AIRPLANE_CHANGE_COMPLETED);
       mHandler.sendEmptyMessage(EVENT_CHECK_AIRPLANE_CHANGE_COMPLETED);
    }

    private boolean isAirplaneModeChangeCompleted() {
        final int simCount = mTelephonyManager.getSimCount();
        final boolean isAirplaneModeOn = isAirplaneModeOn(mContext);
        final boolean isAllSimAbsent = isAllSimAbsent();
        if (DBG) Log.d(TAG, "isAirplaneModeChangeCompleted(), isAirplaneModeOn=" + isAirplaneModeOn
             + ", simCount=" + simCount + ", isAllSimAbsent=" + isAllSimAbsent);
        for (int i = 0; i < simCount; i++) {
            if (DBG) Log.d(TAG, "isAirplaneModeChangeCompleted(), mServiceStates[" + i
                    + "] = " + mServiceStates.get(i));
            if (isAllSimAbsent) {
               if (i != DEFAULT_PHONE_ID) {
                   Log.d(TAG, "isAirplaneModeChangeCompleted(), all sims absent,"
                       + i + " is not default phone id, ignore!");
                   continue;
               }
            } else if (!mTelephonyManager.hasIccCard(i)) {
                Log.d(TAG, "isAirplaneModeChangeCompleted(), sim" + i + " is absent, ignore!");
                continue;
            }
            if (mServiceStates.get(i) == null) return false;
            if (isAirplaneModeOn) {
                if (mServiceStates.get(i) != ServiceState.STATE_POWER_OFF) {
                    return false;
                }
            } else {
                if (mServiceStates.get(i) == ServiceState.STATE_POWER_OFF) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isAllSimAbsent() {
        final int simCount = mTelephonyManager.getSimCount();
        for (int i = 0; i < simCount; i++) {
            if (mTelephonyManager.hasIccCard(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Called when someone clicks on the checkbox preference.
     */
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode, do not update database at this point
        } else {
            setAirplaneModeOn((Boolean) newValue);
        }
        return true;
    }

    public void setAirplaneModeInECM(boolean isECMExit, boolean isAirplaneModeOn) {
        if (isECMExit) {
            // update database based on the current checkbox state
            setAirplaneModeOn(isAirplaneModeOn);
        } else {
            // update summary
            onAirplaneModeChanged();
        }
    }

}
