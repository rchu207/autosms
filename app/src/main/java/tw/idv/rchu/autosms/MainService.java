
package tw.idv.rchu.autosms;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.telephony.SmsManager;
import android.util.Log;

import tw.idv.rchu.autosms.DBExternalHelper.HistoryEntry;
import tw.idv.rchu.autosms.DBExternalHelper.HistoryResult;
import tw.idv.rchu.autosms.DBInternalHelper.ScheduleEntry;
import tw.idv.rchu.autosms.DBInternalHelper.ScheduleRepeat;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainService extends IntentService {
    private static final String TAG = "MainService";

    static final String ACTION_ADD_TO_ALARM = "AddToAlarm";
    static final String ACTION_SEND_SCHEDULE = "send_schedule";
    static final String ACTION_REPORT_SMS_SNET = "report_sms_sent";
    static final String ACTION_NOTIFY_SMS_SNET = "notify_sms_sent";

    static final String BROADCAST_ACTION_SCHEDULE_UPDATED = "tw.com.tadpolemedia.isend.SCHEDULE_UPDATED";
    static final String BROADCAST_ACTION_HISTORY_UPDATED = "tw.com.tadpolemedia.isend.HISTORY_UPDATED";

    static final String EXTRA_SCHEDULE_IDS = "schedule_ids"; // ArrayList<Long>
    static final String EXTRA_HISTORY_ID = "history_id"; // long
    static final String EXTRA_SEND_NOW = "SendNow";
    static final String EXTRA_SMS_CONTACT = "sms_contact";
    static final String EXTRA_SMS_ADDRESS = "sms_address";
    static final String EXTRA_SMS_BODY = "sms_body";
    static final String EXTRA_SMS_RESULT = "sms_result";
    static final String EXTRA_SHOW_NOTIFICATION = "show_notification";

    static final int MAX_TIME_RANGE = 300000;
    static final int NOTIFICATION_SCHEDULER = 1000;
    static final int NOTIFICATION_SENDING = 1001;
    static final int NOTIFICATION_SENT = 1002;

    private class SmsHelper {
        static final String ADDRESS = "address";
        static final String BODY = "body";
        static final String URI_SENT = "content://sms/sent"; // SMS is sent out.

        ContentResolver mResolver;

        public SmsHelper(ContentResolver resolver) {
            mResolver = resolver;
        }

        public void writeSms(String address, String body) {
            ContentValues values = new ContentValues();
            values.put(ADDRESS, address);
            values.put(BODY, body);
            mResolver.insert(Uri.parse(URI_SENT), values);
        }
    }

    private class Schedule {
        public long id;
        public String name;
        public String datetime;
        public int repeat;
        public HashSet<Long> phoneDataIds;
        public String content;
        public long bulkFileId;

        public Schedule() {
            this.id = -1;
            this.name = "";
            this.datetime = "";
            this.repeat = ScheduleRepeat.NONE;
            this.phoneDataIds = new HashSet<Long>(32);
            this.content = "";
            this.bulkFileId = -1;
        }
    }

    public MainService() {
        super("MainService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent(" + intent.getAction() + ")");

        boolean isScheduleUpdated = false;

        if (intent.getAction().equals(ACTION_ADD_TO_ALARM)) {
            // Open database.
            DBInternalHelper dbHelper = new DBInternalHelper(this);
            dbHelper.openWritable();
            DBExternalHelper dbHelper2 = new DBExternalHelper(this);
            dbHelper2.openWritable();

            // Find the nearest schedule and add it to alarm.
            isScheduleUpdated = findTimeoutSchedule(dbHelper, dbHelper2);
            addSchedulesToAlarm(dbHelper);

            // Close database.
            dbHelper.closeWritable();
            dbHelper2.closeWritable();
        } else if (intent.getAction().equals(ACTION_SEND_SCHEDULE)) {
            // Open database.
            DBInternalHelper dbHelper = new DBInternalHelper(this);
            dbHelper.openWritable();
            DBExternalHelper dbHelper2 = new DBExternalHelper(this);
            dbHelper2.openWritable();

            // Get schedules.
            @SuppressWarnings("unchecked")
            ArrayList<Long> ids = (ArrayList<Long>) intent.getSerializableExtra(EXTRA_SCHEDULE_IDS);
            boolean isSendNow = intent.getBooleanExtra(EXTRA_SEND_NOW, false);
            for (Long id : ids) {
                isScheduleUpdated |= sendSchedule(dbHelper, dbHelper2, id, isSendNow);
            }

            // Find the nearest schedule and add it to alarm.
            isScheduleUpdated |= findTimeoutSchedule(dbHelper, dbHelper2);
            addSchedulesToAlarm(dbHelper);

            // Close database.
            dbHelper.closeWritable();
            dbHelper2.closeWritable();
        } else if (intent.getAction().equals(ACTION_REPORT_SMS_SNET)) {
            // Open database.
            DBExternalHelper dbHelper2 = new DBExternalHelper(this);
            dbHelper2.openWritable();

            // Store SMS sent result.
            storeSmsSentResult(dbHelper2, intent);

            // Close database.
            dbHelper2.closeWritable();
        } else if (intent.getAction().equals(ACTION_NOTIFY_SMS_SNET)) {
            // Open database.
            DBExternalHelper dbHelper2 = new DBExternalHelper(this);
            dbHelper2.openWritable();

            // Update history's status.
            int[] result = dbHelper2
                    .generateHistoryStatus(intent.getLongExtra(EXTRA_HISTORY_ID, -1));

            // Show notification.
            if (result != null) {
                showSmsNotification(result[0], result[1]);
                // Notify ListHistoryActivity that schedules are updated.
                Log.d(TAG, "Notify history updated to ListHistoryActivity.");
                final Intent broadcast = new Intent(MainService.BROADCAST_ACTION_HISTORY_UPDATED);
                sendBroadcast(broadcast);                
            }

            // Close database.
            dbHelper2.closeWritable();
        }

        if (isScheduleUpdated) {
            // Notify ListScheduleActivity that schedules are updated.
            Log.d(TAG, "Notify schedule updated to ListScheduleActivity.");
            final Intent broadcast = new Intent(MainService.BROADCAST_ACTION_SCHEDULE_UPDATED);
            sendBroadcast(broadcast);
        }
    }

    // Find the nearest activated schedule and add it to AlarmManager.
    private void addSchedulesToAlarm(DBInternalHelper dbHelper) {
        DateFormat scheduleDateFormat = DBInternalHelper.getScheduleDateFormat();

        // Get activate schedules only.
        String selection = ScheduleEntry.COLUMN_STATUS + " = ?";
        String[] selectionArgs = new String[] {
                String.valueOf(1),
        };
        Cursor cursor = dbHelper.getScheduleCursor(selection, selectionArgs);
        if (cursor == null) {
            Log.e(TAG, "Query activated schedules from database failed!");
            return;
        }

        if (cursor.moveToFirst()) {
            ArrayList<Long> scheduleIds = new ArrayList<Long>();
            String nearestTime = "";

            do {
                // Get schedule ID.
                long id = cursor.getLong(cursor.getColumnIndex(ScheduleEntry._ID));
                String dateTime = cursor.getString(cursor
                        .getColumnIndex(ScheduleEntry.COLUMN_DATETIME));

                if (nearestTime.length() == 0) {
                    nearestTime = dateTime;
                } else if (!nearestTime.equals(dateTime)) {
                    break;
                }

                Log.d(TAG, "Schudule " + id + " will be launched at " + nearestTime);
                scheduleIds.add(id);
            } while (cursor.moveToNext());

            // Get date and time.
            Calendar c = Calendar.getInstance();
            try {
                Date date = scheduleDateFormat.parse(nearestTime);
                c.setTime(date);
            } catch (ParseException e) {
                // Can not parse string, use current time.
            }

            // Create service intent and add it to AlarmManager.
            Intent intent = new Intent(this, MainService.class);
            intent.setAction(ACTION_SEND_SCHEDULE);
            intent.putExtra(EXTRA_SCHEDULE_IDS, scheduleIds);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarm.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pendingIntent);

            // Show the notification.
            showScheduleNotification(c, cursor.getCount());
        } else {
            Log.d(TAG, "No schudule is found, cancel it.");

            // Cancel any pending service intent.
            Intent intent = new Intent(this, MainService.class);
            intent.setAction(ACTION_SEND_SCHEDULE);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarm.cancel(pendingIntent);

            // Cancel the notification.
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(NOTIFICATION_SCHEDULER);
        }

        cursor.close();
    }

    // Schedule is launched on time, send SMS.
    private boolean sendSchedule(DBInternalHelper dbHelper, DBExternalHelper dbHelper2, long id,
            boolean isSendNow) {
        Log.d(TAG, "Ready to send schudule: " + id);

        // Get the schedule.
        String selection = ScheduleEntry._ID + " = ?";
        String[] selectionArgs = new String[] {
                String.valueOf(id),
        };
        Cursor cursor = dbHelper.getScheduleCursor(selection, selectionArgs);
        if (cursor == null) {
            Log.e(TAG, "Query schedule " + id + " from database failed!");
            return false;
        }

        Schedule schedule = null;
        if (cursor.moveToFirst()) {
            long status = cursor.getLong(cursor.getColumnIndex(ScheduleEntry.COLUMN_STATUS));

            // Fill Schedule.
            schedule = new Schedule();
            schedule.id = id;
            schedule.name = cursor.getString(cursor.getColumnIndex(ScheduleEntry.COLUMN_NAME));
            schedule.repeat = cursor
                    .getInt(cursor.getColumnIndex(ScheduleEntry.COLUMN_REPEAT));
            schedule.datetime = cursor.getString(cursor
                    .getColumnIndex(ScheduleEntry.COLUMN_DATETIME));
            String[] dataIdStrs = cursor.getString(cursor
                    .getColumnIndex(ScheduleEntry.COLUMN_PHONE_DATA_IDS)).split(",");
            if (EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BULK == schedule.repeat) {
                schedule.bulkFileId = Long.parseLong(dataIdStrs[0]);
                for (int i = 1; i < dataIdStrs.length; i++) {
                    schedule.phoneDataIds.add(Long.parseLong(dataIdStrs[i]));
                }
            } else {
                for (String idStr : dataIdStrs) {
                    schedule.phoneDataIds.add(Long.parseLong(idStr));
                }
            }

            // Get SMS content
            schedule.content = cursor.getString(cursor
                    .getColumnIndex(ScheduleEntry.COLUMN_SMS_CONTENT));

            // Check schedule is executed in time.
            if (!isSendNow) {
                Calendar nowTime = Calendar.getInstance();
                Calendar scheduleTime = Calendar.getInstance();
                try {
                    Date date = DBInternalHelper.getScheduleDateFormat().parse(schedule.datetime);
                    scheduleTime.setTime(date);
                } catch (ParseException e) {
                    // Can not parse string, use current time.
                }
                if (Math.abs(scheduleTime.getTimeInMillis() - nowTime.getTimeInMillis()) > MAX_TIME_RANGE) {
                    // Update timeout schedule.
                    long historyId = updateTimoutHistory(dbHelper2, schedule);

                    // Schedule is timeout, notify the SMS sent result.
                    if (historyId >= 0) {
                        Intent serviceIntent = new Intent(this, MainService.class);
                        serviceIntent.setAction(ACTION_NOTIFY_SMS_SNET);
                        serviceIntent.putExtra(EXTRA_HISTORY_ID, historyId);
                        startService(serviceIntent);
                    }

                    // The schedule is timeout, set it to deactivate.
                    status = 0;
                }
            }

            // Send SMS.
            if (isSendNow || (status == 1)) {
                // Create history name.
                String name;
                if (isSendNow) {
                    name = getString(R.string.send_now);
                } else {
                    name = schedule.name;
                }

                // Create SMS sending events by contact data IDs.
                SmartElementController smartElement = new SmartElementController(
                        getContentResolver(), dbHelper2, getResources());
                ArrayList<Intent> smsIntents;
                // Filter phone by birthday.
                if (schedule.repeat == EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BIRTHDAY) {
                    Set<Long> birthdayPhoneDataIds = ContactUtility.filterByBirthday(
                            getContentResolver(), schedule.phoneDataIds);
                    smsIntents = ContactUtility.createSmsIntent(
                            getContentResolver(), smartElement, birthdayPhoneDataIds,
                            schedule.content, -1);
                } else {
                    smsIntents = ContactUtility.createSmsIntent(
                            getContentResolver(), smartElement, schedule.phoneDataIds,
                            schedule.content, schedule.bulkFileId);
                }

                if (smsIntents != null && smsIntents.size() > 0) {
                    String datetime = DBInternalHelper.getScheduleDateFormat().format(
                            Calendar.getInstance().getTime());

                    // Start SendSmsService to send SMS.
                    Intent intent = new Intent(this, SendSmsService.class);
                    intent.setAction(SendSmsService.ACTION_SEND);
                    intent.putExtra(SendSmsService.EXTRA_SMS_INTENT, smsIntents);
                    intent.putExtra(SendSmsService.EXTRA_HISTORY_NAME, name);
                    intent.putExtra(SendSmsService.EXTRA_HISTORY_DATETIME, datetime);
                    startService(intent);
                }
            }
        }
        cursor.close();

        // Update the schedule's status.
        boolean isScheduleUpdated = false;
        if ((schedule != null) && !isSendNow) {
            isScheduleUpdated |= updateSchedule(dbHelper, schedule);
        }

        return isScheduleUpdated;
    }

    // Find timeout schedules.
    private boolean findTimeoutSchedule(DBInternalHelper dbHelper, DBExternalHelper dbHelper2) {
        ArrayList<Schedule> timeoutSchedules = new ArrayList<Schedule>(8);

        // Get active schedules only.
        String selection = ScheduleEntry.COLUMN_STATUS + " = ?";
        String[] selectionArgs = new String[] {
                String.valueOf(1),
        };

        // Query database.
        Calendar now = Calendar.getInstance();
        Cursor cursor = dbHelper.getScheduleCursor(selection, selectionArgs);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    Schedule s = new Schedule();

                    // Get date and time.
                    s.datetime = cursor.getString(cursor
                            .getColumnIndex(ScheduleEntry.COLUMN_DATETIME));
                    Calendar dateTime = Calendar.getInstance();
                    try {
                        Date date = DBInternalHelper.getScheduleDateFormat().parse(s.datetime);
                        dateTime.setTime(date);
                    } catch (ParseException e) {
                        // Can not parse string, use current time.
                    }
                    if (dateTime.after(now)) {
                        // No more timeout schedules.
                        break;
                    }

                    // Store the OOF schedule ID and update them later.
                    s.id = cursor.getLong(cursor.getColumnIndex(ScheduleEntry._ID));
                    s.name = cursor
                            .getString(cursor.getColumnIndex(ScheduleEntry.COLUMN_NAME));
                    s.repeat = cursor.getInt(cursor
                            .getColumnIndex(ScheduleEntry.COLUMN_REPEAT));

                    // Get contacts.
                    String[] dataIdStrs = cursor.getString(
                            cursor.getColumnIndex(ScheduleEntry.COLUMN_PHONE_DATA_IDS))
                            .split(",");
                    for (String str : dataIdStrs) {
                        s.phoneDataIds.add(Long.parseLong(str));
                    }

                    // Get SMS content
                    s.content = cursor.getString(cursor
                            .getColumnIndex(ScheduleEntry.COLUMN_SMS_CONTENT));

                    timeoutSchedules.add(s);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        // Update timeout schedules.
        boolean isScheduleUpdated = false;
        long historyId = -1;
        for (Schedule s : timeoutSchedules) {
            isScheduleUpdated |= updateSchedule(dbHelper, s);
            historyId = updateTimoutHistory(dbHelper2, s);
        }

        // Schedule is timeout, notify the SMS sent result.
        if (historyId >= 0) {
            Intent serviceIntent = new Intent(this, MainService.class);
            serviceIntent.setAction(ACTION_NOTIFY_SMS_SNET);
            serviceIntent.putExtra(EXTRA_HISTORY_ID, historyId);
            startService(serviceIntent);
        }

        return isScheduleUpdated;
    }

    private void storeSmsSentResult(DBExternalHelper dbHelper, Intent intent) {
        int resultCode = intent.getIntExtra(EXTRA_SMS_RESULT, Activity.RESULT_OK);

        int result = HistoryResult.PENDING;
        if (resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "SMS result: OK.");
            result = HistoryResult.SUCCESS;

            // Store SMS to system.
            if (intent.hasExtra(EXTRA_SMS_BODY)) {
                SmsHelper smsHelper = new SmsHelper(getContentResolver());
                smsHelper.writeSms(intent.getStringExtra(EXTRA_SMS_ADDRESS),
                        intent.getStringExtra(EXTRA_SMS_BODY));
            }
        } else {
            switch (resultCode) {
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Log.e(TAG, "SMS result: Generic Failure.");
                    result = HistoryResult.ERROR_GENERIC_FAILURE;
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Log.e(TAG, "SMS result: No service.");
                    result = HistoryResult.ERROR_NO_SERVICE;
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Log.e(TAG, "SMS result: Null PDU.");
                    result = HistoryResult.ERROR_NULL_PDU;
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Log.e(TAG, "SMS result: Radio Off.");
                    result = HistoryResult.ERROR_RADIO_OFF;
                    break;
                default:
                    Log.e(TAG, "Unknown result: " + resultCode);
                    // This result may be caused by reach Android sending SMS
                    // rate limit, so stop sending.
                    result = HistoryResult.ERROR_GENERIC_FAILURE;

                    // Stop sending SMS.
                    Intent stopIntent = new Intent(this, SendSmsService.class);
                    stopIntent.setAction(SendSmsService.ACTION_STOP);
                    startService(stopIntent);
                    break;
            }
        }

        // Insert history result.
        Uri historyUri = intent.getData();
        Log.d(TAG, "History: " + historyUri.toString());
        long historyId = Long.decode(historyUri.getAuthority());
        List<String> segments = historyUri.getPathSegments();
        long index = Long.decode(segments.get(0));
        Intent smsIntent = null;
        if (intent.hasExtra(EXTRA_SMS_BODY)) {
            smsIntent = new Intent();
            smsIntent.putExtras(intent);
        }
        dbHelper.updateHistoryResult(historyId, (int) index, result, smsIntent);

        // Notify SMS is sent.
        Intent reportIntent = new Intent(this, SendSmsService.class);
        reportIntent.setAction(SendSmsService.ACTION_REPORT);
        if (Build.VERSION.SDK_INT <= 8) {
            if (intent.hasExtra(EXTRA_SMS_BODY)) {
                // Report SMS sending result only at last one.
                startService(reportIntent);
            }
        } else {
            startService(reportIntent);
        }

        // Show SMS sending result in notification bar.
        if (intent.getIntExtra(EXTRA_SHOW_NOTIFICATION, -1) == NOTIFICATION_SENT) {
            // All SMS intents are sent, notify the SMS sent result.
            Intent serviceIntent = new Intent(this, MainService.class);
            serviceIntent.setAction(ACTION_NOTIFY_SMS_SNET);
            serviceIntent.putExtra(EXTRA_HISTORY_ID, intent.getLongExtra(EXTRA_HISTORY_ID, -1));
            startService(serviceIntent);
        }
    }

    private boolean updateSchedule(DBInternalHelper dbHelper, Schedule schedule) {
        if (schedule == null) {
            return false;
        }

        // Get date and time.
        Calendar dateTime = Calendar.getInstance();
        try {
            Date date = DBInternalHelper.getScheduleDateFormat().parse(schedule.datetime);
            dateTime.setTime(date);
        } catch (ParseException e) {
            // Ignore the exception.
        }

        // Prepare new schedule.
        ContentValues values = new ContentValues();

        if (schedule.repeat == EditScheduleActivity.SCHEDULE_REPEAT_TYPE_NONE) {
            // Deactivate the schedule.
            values.put(ScheduleEntry.COLUMN_STATUS, 0);
        } else {
            // Get current time.
            Calendar nextTime = Calendar.getInstance();
            nextTime.add(Calendar.DATE, 1); // Plus one day to fix infinite
                                            // schedule loop issue.
            nextTime.set(Calendar.HOUR_OF_DAY, 0); // Ignore hour field.
            nextTime.set(Calendar.MINUTE, 0); // Ignore minute field.
            nextTime.set(Calendar.SECOND, 0); // Ignore second field.
            dateTime = MainUtility.renewScheduleDatetime(getContentResolver(), schedule.repeat,
                    schedule.phoneDataIds, dateTime, nextTime);

            if (dateTime.before(nextTime)) {
                // Can not renew schedule, deactivate it.
                values.put(ScheduleEntry.COLUMN_STATUS, 0);
            } else {
                values.put(ScheduleEntry.COLUMN_DATETIME, DBInternalHelper
                        .getScheduleDateFormat().format(dateTime.getTime()));
            }
        }

        // Update new schedule to database.
        if (values.size() > 0) {
            dbHelper.updateSchedule(schedule.id, values);
            return true;
        } else {
            return false;
        }
    }

    private long updateTimoutHistory(DBExternalHelper dbHelper, Schedule schedule) {
        if (schedule == null) {
            return -1;
        }

        // Create history entry.
        ContentValues values = new ContentValues();
        values.put(HistoryEntry.COLUMN_NAME, schedule.name);
        values.put(HistoryEntry.COLUMN_DATETIME, schedule.datetime);
        values.put(HistoryEntry.COLUMN_RESULT, HistoryResult.TIMEOUT);
        long historyId = dbHelper.updateHistory(-1, values);

        ArrayList<Intent> smsIntents;
        // Filter phone by birthday.
        if (schedule.repeat == EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BIRTHDAY) {
            Set<Long> birthdayPhoneDataIds = ContactUtility.filterByBirthday(
                    getContentResolver(), schedule.phoneDataIds);
            smsIntents = ContactUtility.createSmsIntent(getContentResolver(), null,
                    birthdayPhoneDataIds, schedule.content, -1);
        } else {
            smsIntents = ContactUtility.createSmsIntent(getContentResolver(), null,
                    schedule.phoneDataIds, schedule.content, -1);
        }

        // Insert history result.
        if (smsIntents != null) {
            dbHelper.insertHistoryResult(historyId, HistoryResult.TIMEOUT, smsIntents);
        }

        return historyId;
    }

    private void showScheduleNotification(Calendar dateTime, int number) {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_notify_schedule)
                        .setContentTitle(getString(R.string.title_scheduler))
                        .setContentText(getString(R.string.msg_have_activated_schedules))
                        .setContentInfo(String.valueOf(number))
                        .setOngoing(true)
                        .setWhen(dateTime.getTimeInMillis());
        // Creates an explicit intent
        Intent intent = new Intent(this, ListScheduleActivity.class);

        // The stack builder object will contain an artificial back stack for
        // the started Activity. This ensures that navigating backward from
        // the Activity leads out of your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(ListScheduleActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_SCHEDULER, builder.build());
    }

    private void showSmsNotification(int count, int totalCount) {
        String contentText;
        if (count == totalCount) {
            contentText = getString(R.string.msg_send_sms_result_success);
        } else {
            contentText = getString(R.string.msg_send_sms_result_failed);
        }
        String contentInfo = new StringBuffer(32).append(count).append("/").append(totalCount)
                .toString();
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_stat_notify_isend)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(contentText)
                        .setContentInfo(contentInfo)
                        .setAutoCancel(true);

        Intent intent = new Intent(this, ListHistoryActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        stackBuilder.addParentStack(SettingActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(MainService.NOTIFICATION_SENT, builder.build());
    }
}
