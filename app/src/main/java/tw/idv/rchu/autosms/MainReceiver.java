
package tw.idv.rchu.autosms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MainReceiver extends BroadcastReceiver {
    static final String TAG = "MainReceiver";

    static final String ACTION_BOOT_COMPLETE = "android.intent.action.BOOT_COMPLETED";
    static final String ACTION_REPORT_SMS_SENT = "tw.com.tadpolemedia.isend.REPORT_SMS_SENT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_BOOT_COMPLETE)) {
            // Find nearest schedule and add it to AlarmManager.
            Intent serviceIntent = new Intent(context, MainService.class);
            serviceIntent.setAction(MainService.ACTION_ADD_TO_ALARM);
            context.startService(serviceIntent);
        } else if (intent.getAction().equals(ACTION_REPORT_SMS_SENT)) {
            Intent serviceIntent = new Intent(context, MainService.class);
            serviceIntent.setAction(MainService.ACTION_REPORT_SMS_SNET);
            serviceIntent.setData(intent.getData());
            serviceIntent.putExtras(intent);
            serviceIntent.putExtra(MainService.EXTRA_SMS_RESULT, getResultCode());
            context.startService(serviceIntent);
        }
    }
}
