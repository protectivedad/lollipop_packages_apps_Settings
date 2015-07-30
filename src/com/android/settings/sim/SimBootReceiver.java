/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.sim;

import com.android.settings.R;
import com.android.settings.Settings.SimSettingsActivity;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import com.android.internal.telephony.TelephonyIntents;

import com.android.settings.Utils;
import android.util.Log;

import java.util.List;

public class SimBootReceiver extends BroadcastReceiver {
    private static final String TAG = "SimBootReceiver";
    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_ID_SIM_DISABLED = 2;

    private TelephonyManager mTelephonyManager;
    private Context mContext;
    private SubscriptionManager mSubscriptionManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive " + intent);
        String action = intent.getAction();

        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mContext = context;
        mSubscriptionManager = SubscriptionManager.from(mContext);
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionListener);
            if(anySimDisabled()) {
                createSimDisabledNotification(mContext);
            }
        } else if(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
            detectChangeAndNotify();
        }
    }

    private void detectChangeAndNotify() {
        final int numSlots = mTelephonyManager.getSimCount();
        final boolean isInProvisioning = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) == 0;
        boolean notificationSent = false;
        int numSIMsDetected = 0;
        int lastSIMSlotDetected = -1;

        // Do not create notifications on single SIM devices or when provisiong or airplane mode on
        if (numSlots < 2 || isInProvisioning || (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0)) {
            return;
        }

        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            if (TelephonyManager.getDefault().hasIccCard(i)) {
                numSIMsDetected++;
            }
        }

        List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        // if numSIMsDetected is 2, sil.size may be 1 because of another subInfo hasn't been loaded yet,
        // then we should wait all subInfo is loaded(sil.size() == numSIMsDetected), then we can clear the
        // subinfo which no longer exist and show the notification if needed
        if (sil == null || sil.size() < 1) {
            return;
        } else if (sil.size() == numSIMsDetected) {
            // Cancel any previous notifications
            cancelNotification(mContext);
            // Clear defaults for any subscriptions which no longer exist
            mSubscriptionManager.clearDefaultsForInactiveSubIds();
            boolean dataSelected = SubscriptionManager.isUsableSubIdValue(
                    SubscriptionManager.getDefaultDataSubId());
            boolean smsSelected = SubscriptionManager.isUsableSubIdValue(
                    SubscriptionManager.getDefaultSmsSubId());
            // If data and sms defaults are selected, dont show notification (Calls default is optional)
            if (dataSelected && smsSelected) {
                return;
            }

            // Create a notification to tell the user that some defaults are missing
            createNotification(mContext);

            if (sil.size() == 1) {
                // If there is only one subscription, ask if user wants to use if for everything
                Intent intent = new Intent(mContext, SimDialogActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.PREFERRED_PICK);
                intent.putExtra(SimDialogActivity.PREFERRED_SIM, sil.get(0).getSimSlotIndex());
                mContext.startActivity(intent);
            } else if (!dataSelected) {
                // TODO(sanketpadawe): This should not be shown if the user is looking at the
                // SimSettings page - its just annoying
                // If there are mulitple, ensure they pick default data
                Intent intent = new Intent(mContext, SimDialogActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.DEFAULT_DATA_PICK);
                mContext.startActivity(intent);
            }
        }
    }

    private void createNotification(Context context){
        final Resources resources = context.getResources();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_sim_card_alert_white_48dp)
                .setColor(resources.getColor(R.color.sim_noitification))
                .setContentTitle(resources.getString(R.string.sim_notification_title))
                .setContentText(resources.getString(R.string.sim_notification_summary));
        Intent resultIntent = new Intent(context, SimSettingsActivity.class);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                context,
                0,
                resultIntent,
                PendingIntent.FLAG_CANCEL_CURRENT
                );
        builder.setContentIntent(resultPendingIntent);
        NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public static void cancelNotification(Context context) {
        NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private final OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            //detectChangeAndNotify();
        }
    };
    private void createSimDisabledNotification(Context context) {
        final Resources resources = context.getResources();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_sim_card_alert_white_48dp)
                .setColor(resources.getColor(R.color.sim_noitification))
                .setContentTitle(resources.getString(R.string.sim_slot_disabled))
                .setContentText(resources.getString(R.string.sim_notification_summary));
        Intent resultIntent = new Intent(context, SimSettingsActivity.class);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                context,
                0,
                resultIntent,
                PendingIntent.FLAG_CANCEL_CURRENT
                );
        builder.setContentIntent(resultPendingIntent);
        NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_SIM_DISABLED, builder.build());
    }

    public static void cancelSimDisabledNotification(Context context) {
        NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID_SIM_DISABLED);
    }

    private boolean anySimDisabled() {
        for (int i = 0; i < mTelephonyManager.getSimCount(); i++) {
            if(mTelephonyManager.isSimOff(i)) {
                return true;
            }
        }
        return false;
    }
}
