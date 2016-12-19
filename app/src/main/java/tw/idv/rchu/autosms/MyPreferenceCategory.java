
package tw.idv.rchu.autosms;

import android.content.Context;
import android.preference.PreferenceCategory;
import android.view.View;

public class MyPreferenceCategory extends PreferenceCategory {

    public MyPreferenceCategory(Context context) {
        super(context);
    }

    @Override
    protected void onBindView(View view) {
        MainUtility.onBindView(view, getTitle(), getSummary());
    }
}
