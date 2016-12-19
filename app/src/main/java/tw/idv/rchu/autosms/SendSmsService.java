
package tw.idv.rchu.autosms;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import tw.idv.rchu.autosms.DBExternalHelper.HistoryEntry;
import tw.idv.rchu.autosms.DBExternalHelper.HistoryResult;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SendSmsService extends IntentService {
    static final String TAG = "[iSend]SendSmsService";

    static final String ACTION_SEND = "SendSmsServiceSend";
    static final String ACTION_STOP = "SendSmsServiceStop";
    static final String ACTION_REPORT = "SendSmsServiceReport";

    static final String EXTRA_SMS_INTENT = "sms_intent";
    static final String EXTRA_SMS_NOTIFICATION = "sms_notification";
    static final String EXTRA_HISTORY_NAME = "history_name";
    static final String EXTRA_HISTORY_DATETIME = "history_datetime";

    static final String EXTRA_SMS_INDEX = "sms_index";
    static final String EXTRA_SMS_CONTACT = "sms_contact";
    static final String EXTRA_SMS_ADDRESS = "sms_address";
    static final String EXTRA_SMS_BODY = "sms_body";

    static final int SMS_WAITING_TIME = 120; // 2 minutes.

    private boolean mStopSending = false;
    private CountDownLatch mLatch;
    private Thread mLatchThread;

    public SendSmsService() {
        super(TAG);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Overwrite this function to stop sending immediately.
        if (intent.getAction().equals(ACTION_STOP)) {
            mStopSending = true;
            if (mLatchThread != null) {
                mLatchThread.interrupt();
            }
        } else if (intent.getAction().equals(ACTION_REPORT)) {
            if (mLatch != null) {
                Log.d(TAG, "Before countDown.");
                mLatch.countDown();
                Log.d(TAG, "After countDown.");
            } else {
                Log.e(TAG, "Try to count down a NULL latch.");
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals(ACTION_STOP)) {
            return;
        } else if (intent.getAction().equals(ACTION_REPORT)) {
            return;
        }

        if (intent.hasExtra(EXTRA_SMS_INTENT)) {
            // Open database.
            DBExternalHelper dbHelper = new DBExternalHelper(this);
            dbHelper.openWritable();

            sendSms(dbHelper, intent);

            // Close database.
            dbHelper.closeWritable();
        }
    }

    private void sendSms(DBExternalHelper dbHelper, Intent intent) {
        // Create history entry.
        ContentValues values = new ContentValues();
        values.put(HistoryEntry.COLUMN_NAME, intent.getStringExtra(EXTRA_HISTORY_NAME));
        values.put(HistoryEntry.COLUMN_DATETIME, intent.getStringExtra(EXTRA_HISTORY_DATETIME));
        long historyId = dbHelper.updateHistory(-1, values);

        @SuppressWarnings("unchecked")
        ArrayList<Intent> smsIntents = (ArrayList<Intent>) intent
                .getSerializableExtra(EXTRA_SMS_INTENT);

        SmsManager smsManager = SmsManager.getDefault();
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_stat_notify_isend)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.title_send_sms))
                        .setOngoing(true);

        // Setup PendingIntent for notification.
        Intent dialogIntent = new Intent(this, TransparentActivity.class);
        dialogIntent.setAction(TransparentActivity.ACTION_STOP_SENDING);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, dialogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        // Check roaming.
        boolean isSendRoaming = preferences.getBoolean(SettingFragment.KEY_ROAMING, false);
        TelephonyManager telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (!isSendRoaming && telephony.isNetworkRoaming()) {
            values.clear();
            values.put(HistoryEntry.COLUMN_RESULT, HistoryResult.ROAMING);
            dbHelper.updateHistory(historyId, values);

            dbHelper.insertHistoryResult(historyId, HistoryResult.ROAMING, smsIntents);

            // Notify the SMS sent result.
            Intent serviceIntent = new Intent(this, MainService.class);
            serviceIntent.setAction(MainService.ACTION_NOTIFY_SMS_SNET);
            serviceIntent.putExtra(MainService.EXTRA_HISTORY_ID, historyId);
            startService(serviceIntent);
            return;
        }

        // Show notification.
        showSendingNotification(manager, builder, contentIntent, 0, smsIntents.size());

        // Send SMS.
        mLatchThread = Thread.currentThread();        
        for (int i = 0; i < smsIntents.size(); i++) {
            // Stop sending immediately.
            if (mStopSending) {
                Log.e(TAG, "User stops sending SMS manually.");

                // Update history's status.
                values.clear();
                values.put(HistoryEntry.COLUMN_RESULT, HistoryResult.MANUALLY_STOP);
                dbHelper.updateHistory(historyId, values);

                // Insert history results.
                ArrayList<Intent> nonSentIntents = new ArrayList<Intent>(smsIntents.size());
                for (; i < smsIntents.size(); i++) {
                    nonSentIntents.add(smsIntents.get(i));
                }
                dbHelper.insertHistoryResult(historyId, HistoryResult.MANUALLY_STOP, nonSentIntents);

                // All SMS intents are sent, notify the SMS sent result.
                Intent serviceIntent = new Intent(this, MainService.class);
                serviceIntent.setAction(MainService.ACTION_NOTIFY_SMS_SNET);
                serviceIntent.putExtra(MainService.EXTRA_HISTORY_ID, historyId);
                startService(serviceIntent);
                break;
            }

            // Show notification.
            showSendingNotification(manager, builder, contentIntent, i + 1, smsIntents.size());

            // Send text message.
            Intent sms = smsIntents.get(i);
            boolean isLast = (i == smsIntents.size() - 1);
            int count = sendMessage(smsManager, false, historyId, i, sms, isLast);

            // Wait for result.
            long sendTime = SystemClock.uptimeMillis();
            boolean isSentOk = true;
            if (count > 0) {
                if (Build.VERSION.SDK_INT <= 8) {
                    mLatch = new CountDownLatch(1);
                } else {
                    mLatch = new CountDownLatch(count);
                }

                try {
                    Log.d(TAG, "Before await().");
                    isSentOk = mLatch.await(SMS_WAITING_TIME * count, TimeUnit.SECONDS);
                    Log.d(TAG, "After await().");
                    mLatch = null;
                } catch (InterruptedException e) {
                    isSentOk = false;
                }
            }
            sendTime = SystemClock.uptimeMillis() - sendTime;
            
            if (isSentOk) {
                int milliseconds = preferences.getInt(SettingFragment.KEY_SEND_DELAY, SettingFragment.SMS_SEND_DELAY_TIME) * 1000 * count;
                if (i == smsIntents.size() -1) {
                    // No need to delay last SMS intent.
                    milliseconds = 0;
                }
                
                milliseconds -= sendTime;
                if (milliseconds > 0) {
                    Log.d(TAG, "Before sleep():" + milliseconds + " to sending next SMS.");
                    try {
                        Thread.sleep(milliseconds);
                    } catch (InterruptedException e) {}
                    Log.d(TAG, "After sleep().");
                }
            } else {
                // Take too much time to send SMS, stop sending immediately.
                Log.e(TAG, "Take too much time to send SMS, stop sending.");

                // Update history's status.
                values.clear();
                values.put(HistoryEntry.COLUMN_RESULT, HistoryResult.ERROR_GENERIC_FAILURE);
                dbHelper.updateHistory(historyId, values);

                // Insert history results.
                ArrayList<Intent> nonSentIntents = new ArrayList<Intent>(smsIntents.size());
                for (i++; i < smsIntents.size(); i++) {
                    nonSentIntents.add(smsIntents.get(i));
                }
                dbHelper.insertHistoryResult(historyId, HistoryResult.ERROR_GENERIC_FAILURE,
                        nonSentIntents);

                // All SMS intents are sent, notify the SMS sent result.
                Intent serviceIntent = new Intent(this, MainService.class);
                serviceIntent.setAction(MainService.ACTION_NOTIFY_SMS_SNET);
                serviceIntent.putExtra(MainService.EXTRA_HISTORY_ID, historyId);
                startService(serviceIntent);
                break;                
            }
        }
        mLatchThread = null;

        // Cancel the progress notification.
        manager.cancel(MainService.NOTIFICATION_SENDING);
    }

    private void showSendingNotification(NotificationManager manager,
            NotificationCompat.Builder builder, PendingIntent contentIntent, int count,
            int totalCount) {
        builder.setContentInfo(count + "/" + totalCount);
        if (Build.VERSION.SDK_INT <= 10) {
            builder.setContentText(getString(R.string.title_send_sms) + ": " + count + "/"
                    + totalCount);
        } else if (Build.VERSION.SDK_INT >= 14) {
            builder.setProgress(totalCount, count, false);
        }

        manager.notify(MainService.NOTIFICATION_SENDING, builder.build());
    }

    private int sendMessage(SmsManager smsManager, boolean isFixedLimit, long historyId,
            int smsIndex, Intent intent, boolean isLast) {
        if (intent == null) {
            return 0;
        }

        String contact = intent.getStringExtra(EXTRA_SMS_CONTACT);
        String address = intent.getStringExtra(EXTRA_SMS_ADDRESS);
        String body = intent.getStringExtra(EXTRA_SMS_BODY);
        if (contact == null || address == null || body == null) {
            return 0;
        }

        Log.d(TAG, "Send SMS to: " + contact + "(" + address + ")");

        // Divide SMS message.
        ArrayList<String> bodys;
        if (isFixedLimit) {
            bodys = divideMessage(body);
        } else {
            bodys = smsManager.divideMessage(body);
        }
        if (bodys == null || bodys.size() <= 0) {
            return 0;
        }

        // Create PendingIntent for every divided messages.
        ArrayList<PendingIntent> pendingIntents = new ArrayList<PendingIntent>(bodys.size());
        for (int j = 0; j < bodys.size(); j++) {
            Log.d(TAG, "SMS body (" + (j + 1) + "): " + bodys.get(j));

            Intent broadcast = new Intent(this, MainReceiver.class);
            broadcast.setAction(MainReceiver.ACTION_REPORT_SMS_SENT);
            broadcast.setData(Uri.parse(String
                    .format("history://%d/%d/%d", historyId, smsIndex, j)));
            broadcast.putExtra(MainService.EXTRA_HISTORY_ID, historyId);
            if (j == bodys.size() - 1) {
                // Last part of SMS body,
                broadcast.putExtra(MainService.EXTRA_SMS_CONTACT, contact);
                broadcast.putExtra(MainService.EXTRA_SMS_ADDRESS, address);
                broadcast.putExtra(MainService.EXTRA_SMS_BODY, body);

                // Last SMS intent, notify the SMS sent result.
                if (isLast) {
                    broadcast.putExtra(MainService.EXTRA_SHOW_NOTIFICATION,
                            MainService.NOTIFICATION_SENT);
                }
            }

            // Create PendingIntent.
            pendingIntents
                    .add(PendingIntent.getBroadcast(getApplicationContext(), 0, broadcast, 0));
        }

        // Send SMS message.
        try {
            smsManager.sendMultipartTextMessage(address, null, bodys, pendingIntents, null);
            return bodys.size();
        } catch (NullPointerException e) {
            if (!isFixedLimit) {
                Log.e(TAG, "NullPointerException happens, use my message divider.");
                return sendMessage(smsManager, true, historyId, smsIndex, intent, isLast);
            } else {
                Log.e(TAG, "NullPointerException happens, can't recover it.");
                return 0;
            }
        }
    }

    private ArrayList<String> divideMessage(String body) {
        ArrayList<String> bodys = new ArrayList<String>(10);
        if (body != null) {
            for (int i = 0; i < body.length(); i += 67) {
                if (i + 67 > body.length()) {
                    bodys.add(body.substring(i));
                } else {
                    bodys.add(body.substring(i, i + 67));
                }
            }
        }

        return bodys;
    }
}
