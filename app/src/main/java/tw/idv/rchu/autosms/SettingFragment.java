
package tw.idv.rchu.autosms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.util.ArrayList;

public class SettingFragment extends ListFragment implements OnPreferenceClickListener {
    static final String TAG = "[iSend]SettingFragment";

    static final String KEY_CATEGORY_SMS = "cat_sms";
    static final String KEY_CATEGORY_APPLICATION = "cat_application";
    
    static final String KEY_BUY_PREMIUM = "pref_buy_premium";
    static final String KEY_CONFIRM_SEND = "pref_confirm_send";
    static final String KEY_ROAMING = "pref_roaming";
    static final String KEY_HISTORY = "pref_history";
    static final String KEY_SEND_DELAY = "pref_send_delay";

    static final int SMS_SEND_DELAY_TIME = 30; // 30 seconds.

    private SettingActivity mActivity;
    private SharedPreferences mSharedPref;
    private AlertDialog mAlertDialog;    

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (SettingActivity) activity;
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(mActivity);        
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        setupPreferenceScreen();

        return view;
    }

    @Override
    public void onStop() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        super.onStop();        
    }

    @Override
    public void onDetach() {
        mActivity = null;
        super.onDetach();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Preference pref = (Preference) l.getItemAtPosition(position);
        Log.d(TAG, "onListItemClick: " + pref.getKey());

        if (pref instanceof CheckBoxPreference) {
            CheckBoxPreference checkPref = (CheckBoxPreference) pref;
            checkPref.setChecked(!checkPref.isChecked());
        }

        OnPreferenceClickListener listener = pref.getOnPreferenceClickListener();
        if (listener != null) {
            listener.onPreferenceClick(pref);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(KEY_HISTORY)) {
            startActivity(preference.getIntent());
        } else if (preference.getKey().equals(KEY_SEND_DELAY)) {
            // Get items and checked index.
            final String[] items = mActivity.getResources().getStringArray(R.array.pref_send_delay_names);
            int seconds = mSharedPref.getInt(KEY_SEND_DELAY, SMS_SEND_DELAY_TIME);
            int checkedItem;
            for (checkedItem = 0; checkedItem < items.length; checkedItem++) {
                if (items[checkedItem].equals(String.valueOf(seconds))) {
                    break;
                }
            }
            if (checkedItem >= items.length) {
                checkedItem = 0;
            }
            
            // Display delay time selection dialog.
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }
            mAlertDialog = new AlertDialog.Builder(mActivity)
                .setTitle(getString(R.string.msg_set_delay_time))
                .setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
                    
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int seconds = Integer.parseInt(items[which]);
                        
                        SharedPreferences.Editor spe = mSharedPref.edit();
                        spe.putInt(KEY_SEND_DELAY, seconds);
                        spe.commit();
                        updateSendDelayPreference(seconds);
                        
                        dialog.dismiss();
                    }
                })
                .create();                
            mAlertDialog.show();
        }

        return true;
    }

    private void setupPreferenceScreen() {
        ArrayList<Preference> items = new ArrayList<Preference>();
        Preference pref;
        Intent intent;

        // Add SMS category.
        PreferenceCategory category = new MyPreferenceCategory(mActivity);
        category.setKey(KEY_CATEGORY_SMS);
        category.setTitle(R.string.short_message);
        items.add(category);        

        // CONFIRM_SEND preference.
        pref = new MyCheckBoxPreference(mActivity, mSharedPref);
        pref.setKey(KEY_CONFIRM_SEND);
        pref.setTitle(R.string.pref_confirm_send);
        pref.setLayoutResource(R.layout.my_checkbox_preference);
        ((CheckBoxPreference) pref).setSummaryOn(R.string.pref_confirm_send_summary_on);
        ((CheckBoxPreference) pref).setSummaryOff(R.string.pref_confirm_send_summary_off);
        items.add(pref);

        // ROAMING preference.
        pref = new MyCheckBoxPreference(mActivity, mSharedPref);
        pref.setKey(KEY_ROAMING);
        pref.setTitle(R.string.pref_roaming);
        pref.setLayoutResource(R.layout.my_checkbox_preference);
        ((CheckBoxPreference) pref).setSummaryOn(R.string.pref_roaming_summary_on);
        ((CheckBoxPreference) pref).setSummaryOff(R.string.pref_roaming_summary_off);
        items.add(pref);

        // SEND_DEALY preference.
        pref = new MyPreference(mActivity);
        pref.setKey(KEY_SEND_DELAY);
        pref.setTitle(R.string.pref_send_delay);
        int seconds = mSharedPref.getInt(KEY_SEND_DELAY, SMS_SEND_DELAY_TIME);
        String summary = String.format(getString(R.string.pref_send_delay_summary), seconds);
        pref.setSummary(summary);
        pref.setOnPreferenceClickListener(this);
        pref.setLayoutResource(R.layout.my_checkbox_preference);
        items.add(pref);
        
        // HISTORY preference.
        pref = new MyPreference(mActivity);
        pref.setKey(KEY_HISTORY);
        pref.setTitle(R.string.pref_history);
        intent = new Intent(mActivity, ListHistoryActivity.class);
        pref.setIntent(intent);
        pref.setOnPreferenceClickListener(this);
        pref.setLayoutResource(R.layout.my_checkbox_preference);
        items.add(pref);

        // Update UI.
        SettingAdapter adapter = new SettingAdapter(mActivity, items);
        setListAdapter(adapter);
    }

    public void updatePremiumPreference(boolean isPremium, int scheduleLimit, int contactLimit) {
        SettingAdapter adapter = (SettingAdapter) getListAdapter();
        if (adapter == null) {
            return;
        }
        Preference pref = adapter.findPreference(KEY_BUY_PREMIUM);
        if (pref == null) {
            return;
        }

        // Update UI.
        if (isPremium) {
            adapter.remove(pref);
        }

        // Update UI.
        adapter.notifyDataSetChanged();
    }
    
    public void updateSendDelayPreference(int seconds) {
        SettingAdapter adapter = (SettingAdapter) getListAdapter();
        if (adapter == null) {
            return;
        }
        Preference pref = adapter.findPreference(KEY_SEND_DELAY);
        if (pref == null) {
            return;
        }

        // Update UI.
        String summary = String.format(getString(R.string.pref_send_delay_summary), seconds);
        pref.setSummary(summary);
        adapter.notifyDataSetChanged();
    }    
}
