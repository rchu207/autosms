
package tw.idv.rchu.autosms;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;

import java.util.HashSet;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = "[iSend]MainActivity";

    private static final int EDIT_SMS_CONTENT = 1;
    private static final int LIST_FILE = 2;

    private HashSet<Long> mContactDataIds;
    private CharSequence mSmsContent;

    private AlertDialog mAlertDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialization.
        mContactDataIds = new HashSet<Long>();
        mSmsContent = "";

        // Check SMS support.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            // Show alert dialog to notify user can't send SMS.
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }
            mAlertDialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.msg_no_telephony_radio))
                    .setNeutralButton(android.R.string.ok, null).create();
            mAlertDialog.show();
        }

        // Launch MainService to add the schedule to AlarmManager.
        Intent serviceIntent = new Intent(this, MainService.class);
        serviceIntent.setAction(MainService.ACTION_ADD_TO_ALARM);
        startService(serviceIntent);
    }

    @Override
    protected void onStop() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");

        super.onDestroy();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EDIT_SMS_CONTENT) {
            if ((resultCode == RESULT_OK) && (data != null)) {
                // Store contacts.
                if (data.hasExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS)) {
                    mContactDataIds = (HashSet<Long>) data
                            .getSerializableExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS);
                }

                // Store the SMS content.
                if (data.hasExtra(WriteSmsActivity.EXTRA_SMS_CONTENT)) {
                    mSmsContent = data.getCharSequenceExtra(WriteSmsActivity.EXTRA_SMS_CONTENT);
                }
            } else {
                mContactDataIds = new HashSet<Long>();
                mSmsContent = "";
            }
        } else if (requestCode == LIST_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Intent intent = new Intent(this, ListScheduleActivity.class);
            intent.setAction(Intent.ACTION_INSERT);
            intent.setData(data.getData());

            startActivity(intent);            
        }
    }

    public void onSmsButtonClicked(View view) {
        Intent intent = new Intent(this, WriteSmsActivity.class);
        intent.setAction(Intent.ACTION_SEND);

        if (mContactDataIds.size() > 0) {
            intent.putExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS, mContactDataIds);
        }

        if (mSmsContent.length() > 0) {
            intent.putExtra(WriteSmsActivity.EXTRA_SMS_CONTENT, mSmsContent);
        }

        startActivityForResult(intent, EDIT_SMS_CONTENT);
    }

    public void onScheduleButtonClicked(View view) {
        Intent intent = new Intent(this, ListScheduleActivity.class);
        intent.setAction(Intent.ACTION_VIEW);

        startActivity(intent);
    }

    public void onTemplateButtonClicked(View view) {
        Intent intent = new Intent(this, ListTemplateActivity.class);
        intent.setAction(Intent.ACTION_VIEW);

        startActivity(intent);
    }
    
    public void onCsvImportButtonClicked(View view) {
        // Start ListFileActivity to pick a CSV file.
        Intent intent = new Intent(this, ListFileActivity.class);
        intent.setAction(Intent.ACTION_PICK);

        startActivityForResult(intent, LIST_FILE);
    } 

    public void onSosButtonClicked(View view) {
        Intent intent = new Intent(this, ListScheduleActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setData(Uri.fromParts("sos", "//schedules/0", ""));

        startActivity(intent);   
    }    
    
    public void onSettingButtonClicked(View view) {
        Intent intent = new Intent(this, SettingActivity.class);
        intent.setAction(Intent.ACTION_EDIT);

        startActivity(intent);
    }
}
