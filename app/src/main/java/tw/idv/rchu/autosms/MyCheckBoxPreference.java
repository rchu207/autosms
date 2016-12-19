
package tw.idv.rchu.autosms;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.CheckBoxPreference;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

public class MyCheckBoxPreference extends CheckBoxPreference {
    static final String TAG = "[iSend]MyCheckBoxPreference";

    private SharedPreferences mSharedPref;
    private TextView mTextView;
    private CheckBox mCheckBox;

    public MyCheckBoxPreference(Context context, SharedPreferences sp) {
        super(context);
        mSharedPref = sp;
    }

    @Override
    protected void onBindView(View view) {
        CharSequence title = getTitle();
        final TextView titleView = (TextView) view.findViewById(android.R.id.title);
        if (titleView != null) {
            if (title != null && title.length() > 0) {
                titleView.setText(title);
                titleView.setVisibility(View.VISIBLE);
            } else {
                titleView.setVisibility(View.GONE);
            }
        }
        
        final ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
        if (imageView != null) {
            imageView.setVisibility(View.GONE);
        }
        
        mTextView = (TextView) view.findViewById(android.R.id.summary);
        mCheckBox = (CheckBox) view.findViewById(android.R.id.checkbox);

        // Update UI.
        boolean checked = mSharedPref.getBoolean(getKey(), false);
        super.setChecked(checked);
        syncView(checked);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);

        // Update SharedPreferences.
        SharedPreferences.Editor spe = mSharedPref.edit();
        spe.putBoolean(getKey(), checked);
        spe.commit();

        // Update UI.
        syncView(checked);
    }

    private void syncView(boolean checked) {
        Log.d(TAG, "syncView:" + checked);

        // Update summary.
        CharSequence summary;        
        if (checked) {
            summary = getSummaryOn();
        } else {
            summary = getSummaryOff();
        }
        if (summary == null || summary.length() == 0) {
            summary = getSummary();   
        }
        
        if (mTextView != null) {
            if (summary != null && summary.length() > 0) {
                mTextView.setText(summary);
                mTextView.setVisibility(View.VISIBLE);
            } else {
                mTextView.setVisibility(View.GONE);
            }
        }

        // Update check box.
        if (mCheckBox != null) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        }
    }
}
