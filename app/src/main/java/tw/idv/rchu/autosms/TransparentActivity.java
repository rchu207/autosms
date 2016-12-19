
package tw.idv.rchu.autosms;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class TransparentActivity extends Activity {
    static final String TAG = "TransparentActivity";

    static final String ACTION_STOP_SENDING = "StopSending";

    AlertDialog mAlertDialog;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = new Intent(this, SendSmsService.class);
        intent.setAction(SendSmsService.ACTION_STOP);

        AlertDialog.Builder builder = createBuilder();
        mAlertDialog = builder.setMessage(getString(R.string.msg_stop_sending_sms))
                .setPositiveButton(android.R.string.ok, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        // Stop sending SMS.
                        startService(intent);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        mAlertDialog.setOnDismissListener(new OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dialog) {
                // Close the activity immediately.
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Show the dialog.
        if (mAlertDialog != null) {
            mAlertDialog.show();
        } else {
            finish();
        }
    }
    
    @Override
    protected void onStop() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            // Not free the mAlertDialog for later use.
        }
        super.onStop();
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private AlertDialog.Builder createBuilder() {
        if (MainUtility.hasHoneycomb()) {
            return new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_LIGHT);            
        } else {
            return new AlertDialog.Builder(this);
        }        
    }
    
}
