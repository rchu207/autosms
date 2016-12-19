
package tw.idv.rchu.autosms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;

public class SettingActivity extends ActionBarActivity {
    static final String TAG = "[iSend]SettingActivity";

    static final String EXTRA_IS_PURCHASE = "is_purchase";
    
    boolean mIsPremium = true;

    private Activity mActivity;
    private AlertDialog mAlertDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_setting);

        mActivity = this;

        // Setup action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Add Setting fragment.
        addFragment(mIsPremium);

        // Update UI.
        updateLimit(mIsPremium);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Go home
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addFragment(boolean isPremium) {
        // Add Setting fragment.
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        SettingFragment frag = new SettingFragment();
        transaction.replace(R.id.content_frame, frag);
        transaction.commit();
        getSupportFragmentManager().executePendingTransactions();
    }
    
    private void updateLimit(boolean isPremium) {
        int scheduleLimit = 1;
        int contactLimit = 20;
        if (isPremium) {
            // Extend the schedule and contact limit.
            scheduleLimit = 100;
            contactLimit = 500;
        }
        
        // Store schedule limit into SharedPreferences.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt("schedule_limit", scheduleLimit);
        spe.putInt("contact_limit", contactLimit);
        spe.commit();
        
        // Update UI.
        SettingFragment frag = (SettingFragment) getSupportFragmentManager()
                .findFragmentById(R.id.content_frame);
        if (frag != null) {
            frag.updatePremiumPreference(isPremium, scheduleLimit, contactLimit);
        }            
    }    

}
