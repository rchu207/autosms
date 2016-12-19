
package tw.idv.rchu.autosms;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.List;

/**
 * class SettingAdapter
 * 
 * @author rchu
 */
public class SettingAdapter extends ArrayAdapter<Preference> {
    private List<Preference> mItems;

    public enum RowType {
        SETTING_HEADER,
        SETTING_ITEM,
    }

    public SettingAdapter(Context context, List<Preference> items) {
        super(context, 0, items);
        mItems = items;
    }

    @Override
    public int getViewTypeCount() {
        return RowType.values().length;
    }

    @Override
    public int getItemViewType(int position) {
        Preference item = mItems.get(position);
        if (item instanceof PreferenceCategory) {
            return RowType.SETTING_HEADER.ordinal();
        } else {
            return RowType.SETTING_ITEM.ordinal();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return mItems.get(position).getView(convertView, parent);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return mItems.get(position).isEnabled();
    }

    public Preference findPreference(String key) {
        for (Preference pref : mItems) {
            if (pref.getKey().equals(key)) {
                return pref;
            }
        }

        return null;
    }

    public int findPreferenceIndex(String key) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getKey().equals(key)) {
                return i;
            }
        }

        return -1;
    }
}
