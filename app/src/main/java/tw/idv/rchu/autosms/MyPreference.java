
package tw.idv.rchu.autosms;

import android.content.Context;
import android.preference.Preference;
import android.view.View;

public class MyPreference extends Preference {

    public MyPreference(Context context) {
        super(context);
    }
    
    protected void onBindView(View view) {
        MainUtility.onBindView(view, getTitle(), getSummary());
        
        View checkBox = view.findViewById(android.R.id.checkbox);
        if (checkBox != null) {
            checkBox.setVisibility(View.GONE);
        }
    }
}
