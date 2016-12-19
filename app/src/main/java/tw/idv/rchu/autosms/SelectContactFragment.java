
package tw.idv.rchu.autosms;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.app.ListFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;

public class SelectContactFragment extends ListFragment {
    static final String TAG = "SelectContactFragment";

    private HashSet<Long> mContactDataIds = new HashSet<Long>();
    private boolean mIsBirthdayMode;
    private MyListAdapter mAdapter;
    private int mLimit;
    private Button mOkButton;
    private Toast mBuyToast;

    @Override
    @SuppressWarnings("unchecked")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getActivity().getIntent();
        if (intent.hasExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS)) {
            // Get selected contacts.
            mContactDataIds = (HashSet<Long>) intent
                    .getSerializableExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS);
        }
        mIsBirthdayMode = intent.getBooleanExtra(AddContactActivity.EXTRA_BIRTHDAY_MODE, false);

        // Get contact limit.
        if (MainUtility.isDebug(getActivity().getApplicationInfo())) {
            mLimit = 500;
        } else {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            mLimit = sp.getInt("contact_limit", 20);
            if (mLimit < 20)
                mLimit = 20;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_select_contact, container, false);
        populateContactList();

        EditText editText = (EditText) v.findViewById(R.id.editTextSearch);
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.d(TAG, "onTextChanged:" + s);
                mAdapter.getFilter().filter(s);
            }

        });

        // Setup "Ok" button's description.
        mOkButton = (Button) v.findViewById(R.id.button_select_ok);
        String buttonText = getString(android.R.string.ok);
        if (mContactDataIds.size() >= 1) {
            buttonText += " (" + mContactDataIds.size() + ")";
            mOkButton.setEnabled(true);
        } else {
            mOkButton.setEnabled(false);
        }
        mOkButton.setText(buttonText);

        // If no contacts is shown, show a message to tell user the reason.
        if (mAdapter.getCount() <= 0) {
            Toast toast = Toast.makeText(getActivity(), getString(R.string.msg_no_phone_contacts),
                    Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }

        return v;
    }

    /**
     * Populate the contact list based on account currently selected in the
     * account spinner.
     */
    private void populateContactList() {
        // Build adapter with contact entries
        Cursor cursor;
        if (mIsBirthdayMode) {
            cursor = ContactUtility.getPhoneCursorWithBirthday(getActivity().getContentResolver(), "");            
        } else {
            cursor = ContactUtility.getPhoneCursor(getActivity().getContentResolver(), "");
        }
        String[] fields = new String[] {
                // ContactsContract.Contacts.PHOTO_ID,
                ContactsContract.Data.DISPLAY_NAME,
                Phone.TYPE,
                Phone.NUMBER
        };
        mAdapter = new MyListAdapter(getActivity(),
                R.layout.contact_entry, cursor,
                fields, new int[] {
                        // R.id.contactEntryPhoto,
                        R.id.contactEntryName,
                        R.id.contactEntryPhoneType,
                        R.id.contactEntryPhoneNumber
                });
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)) {
                    // Get display name.
                    String name = cursor.getString(columnIndex);

                    // Try to get birthday.
                    int index = cursor.getColumnIndex(ContactUtility.COLUMN_BIRTHDAY);
                    if (index >= 0) {                        
                        name += " (" + cursor.getString(index) + ")";
                    }

                    // Update UI.
                    TextView textView = (TextView) view;
                    textView.setText(name);
                    return true;
                } else if (columnIndex == cursor.getColumnIndex(Phone.TYPE)) {
                    // Get phone type.
                    int phoneType = cursor.getInt(columnIndex);
                    String phoneLabel = "";
                    if (phoneType == Phone.TYPE_CUSTOM) {
                        phoneLabel = cursor.getString(cursor.getColumnIndex(Phone.LABEL));
                    }

                    // Update UI.
                    TextView textView = (TextView) view;
                    textView.setText(Phone.getTypeLabel(getActivity().getResources(), phoneType,
                            phoneLabel));
                    textView.append(":");

                    return true;
                }
                return false;
            }

        });

        mAdapter.setFilterQueryProvider(new FilterQueryProvider() {
            public Cursor runQuery(CharSequence constraint) {
                if (mIsBirthdayMode) {
                    return ContactUtility.getPhoneCursorWithBirthday(getActivity().getContentResolver(), constraint.toString());            
                } else {
                    return ContactUtility.getPhoneCursor(getActivity().getContentResolver(), constraint.toString());
                }
            }
        });

        setListAdapter(mAdapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Log.d(TAG, "onListItemClick:" + position + "," + id);
        // Reverse contact selection.
        CheckBox cb = (CheckBox) v.findViewById(R.id.checkBoxAdd);
        if ((cb != null) && cb.isEnabled()) {
            cb.setChecked(!cb.isChecked());
        } else {
            return;
        }

        // Add/Remove the selected contact.
        if (cb.isChecked()) {
            mContactDataIds.add(id);
        } else {
            mContactDataIds.remove(id);
        }

        // If over the limit, then undo checking.
        if (mContactDataIds.size() > mLimit) {
            mContactDataIds.remove(id);
            cb.setChecked(false);
            if (mLimit < 100) {
                if (mBuyToast != null) {
                    mBuyToast.cancel();
                }

                // Create "Buy Premium" toast.
                mBuyToast = Toast.makeText(getActivity(), R.string.msg_want_buy_premium, Toast.LENGTH_SHORT);
                mBuyToast.show();
            }
        }

        // Setup "Ok" button's description.
        String buttonText = getString(android.R.string.ok);
        if (mContactDataIds.size() >= 1) {
            buttonText += " (" + mContactDataIds.size() + ")";
            mOkButton.setEnabled(true);
        } else {
            mOkButton.setEnabled(false);
        }
        mOkButton.setText(buttonText);
    }

    public HashSet<Long> getContacts() {
        return mContactDataIds;
    }

    public void setContacts(HashSet<Long> contacts) {
        if (contacts != null) {
            mContactDataIds = contacts;
        }
    }

    public class MyListAdapter extends SimpleCursorAdapter {
        static final String TAG = "MyListAdapter";

        public MyListAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);

            long id = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID));
            CheckBox cb = (CheckBox) view.findViewById(R.id.checkBoxAdd);
            if (cb != null) {
                // Set checked or not.
                cb.setChecked(mContactDataIds.contains(id));
            }
        }
    }
}
