
package tw.idv.rchu.autosms;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class SelectGroupFragment extends Fragment {
    static final String TAG = "SelectGroupFragment";

    MyExpandableListAdapter mAdapter;

    HashSet<Long> mContactGroupIds = new HashSet<Long>();
    HashSet<Long> mContactDataIds = new HashSet<Long>();
    private SparseArray<String> mBirthdays;
    private int mLimit;
    private Button mOkButton;
    private Toast mBuyToast;

    class ContactGroup {
        public Long mGroupId;
        public ArrayList<Long> mDataIds;
    }

    HashMap<Long, ContactGroup> mGroups = new HashMap<Long, ContactGroup>();

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
        boolean isBirthdayMode = intent.getBooleanExtra(AddContactActivity.EXTRA_BIRTHDAY_MODE,
                false);
        if (isBirthdayMode) {
            mBirthdays = new SparseArray<String>();
        }

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
        View view = inflater.inflate(R.layout.fragment_select_group, container, false);
        populateGroupList(view);

        // Setup "Ok" button's description.
        mOkButton = (Button) view.findViewById(R.id.button_select_ok);
        String buttonText = getString(android.R.string.ok);
        if (mContactDataIds.size() >= 1) {
            buttonText += " (" + mContactDataIds.size() + ")";
            mOkButton.setEnabled(true);
        } else {
            mOkButton.setEnabled(false);
        }
        mOkButton.setText(buttonText);

        // If no group is shown, show a message to tell user the reason.
        if (mAdapter.getGroupCount() <= 0) {
            Toast toast = Toast.makeText(getActivity(), getString(R.string.msg_no_phone_groups),
                    Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }

        return view;
    }

    /**
     * Populate the group list.
     */
    private void populateGroupList(View view) {
        // Build adapter with contact entries
        Cursor cursor = ContactUtility.getGroupCursor(getActivity().getContentResolver());
        String[] parentFields = new String[] {
                ContactsContract.Groups.TITLE
        };
        String[] childFields = new String[] {
                ContactsContract.Data.DISPLAY_NAME,
                Phone.TYPE,
                Phone.NUMBER
        };

        mAdapter = new MyExpandableListAdapter(
                getActivity(),
                cursor,
                R.layout.group_entry, // parent layout
                R.layout.contact_entry, // child layout
                parentFields, // parent cursor column
                new int[] {
                        R.id.groupEntryName
                }, // parent layout mapping id
                childFields, // child cursor column
                new int[] {
                        R.id.contactEntryName,
                        R.id.contactEntryPhoneType,
                        R.id.contactEntryPhoneNumber
                }); // child layout mapping id

        mAdapter.setOnGroupClickListener(new OnGroupClickListener() {

            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition,
                    long id) {
                CheckBox cb = (CheckBox) v;

                // Add/Remove the selected group.
                if (cb.isChecked()) {
                    addGroup(id);
                } else {
                    removeGroup(id);
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

                return false;
            }
        });

        mAdapter.setViewBinder(new SimpleCursorTreeAdapter.ViewBinder() {

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)) {
                    // Get display name.
                    String name = cursor.getString(columnIndex);

                    // Get birthday.
                    if (mBirthdays != null) {
                        int contactId = cursor.getInt(cursor.getColumnIndex(Phone.CONTACT_ID));
                        String birthday = mBirthdays.get(contactId);
                        if (birthday != null && birthday.length() > 0) {
                            name += " (" + birthday + ")";
                        }
                    }

                    // Update UI.
                    TextView textView = (TextView) view;
                    textView.setText(name);
                    return true;
                } else if (columnIndex == cursor.getColumnIndex(Phone.TYPE)) {
                    int phoneType = cursor.getInt(columnIndex);
                    String phoneLabel = "";
                    if (phoneType == Phone.TYPE_CUSTOM) {
                        phoneLabel = cursor.getString(cursor.getColumnIndex(Phone.LABEL));
                    }
                    TextView textView = (TextView) view;
                    textView.setText(Phone.getTypeLabel(getActivity().getResources(), phoneType,
                            phoneLabel));
                    textView.append(":");

                    return true;
                }
                return false;
            }

        });

        ExpandableListView expListView = (ExpandableListView) view.findViewById(android.R.id.list);
        // expListView.setGroupIndicator(null);
        expListView.setOnChildClickListener(new OnChildClickListener() {

            @Override
            public boolean onChildClick(ExpandableListView parent, View view, int groupPosition,
                    int childPosition, long id) {
                Log.d(TAG, "onChildClick():" + groupPosition + "," + childPosition + "," + id);

                // Reverse contact selection.
                CheckBox cb = (CheckBox) view.findViewById(R.id.checkBoxAdd);
                if ((cb != null) && cb.isEnabled()) {
                    cb.setChecked(!cb.isChecked());
                } else {
                    return false;
                }

                // Add/Remove the selected contact.
                if (cb.isChecked()) {
                    mContactDataIds.add(id);
                } else {
                    removeContact(id);
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

                return false;
            }
        });
        expListView.setAdapter(mAdapter);
    }

    public HashSet<Long> getContacts() {
        return mContactDataIds;
    }

    public void setContacts(HashSet<Long> contacts) {
        if (contacts != null) {
            mContactDataIds = contacts;
        }
    }

    private void addGroup(Long id) {
        mContactGroupIds.add(id);

        // Add all contacts in the group.
        ContactGroup g = mGroups.get(id);
        if (g == null) {
            // Get contacts in the group from DB.
            g = new ContactGroup();
            g.mGroupId = id;
            g.mDataIds = new ArrayList<Long>();

            Cursor cursor = getGroupPhoneCurosr(id);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        long dataId = cursor.getLong(cursor
                                .getColumnIndex(ContactsContract.Data._ID));
                        g.mDataIds.add(dataId);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
            mGroups.put(id, g);
        }

        for (Long dataId : g.mDataIds) {
            mContactDataIds.add(dataId);

            // If over the limit, then undo checking.
            if (mContactDataIds.size() > mLimit) {
                mContactDataIds.remove(dataId);
                break;
            }
        }

        // Update UI status.
        mAdapter.notifyDataSetChanged();
    }

    private void removeGroup(Long id) {
        mContactGroupIds.remove(id);

        // Remove all contacts in the group.
        ContactGroup g = mGroups.get(id);
        if (g != null) {
            for (Long dataId : g.mDataIds) {
                mContactDataIds.remove(dataId);
            }

            // Update UI status.
            mAdapter.notifyDataSetChanged();
        }
    }

    public void removeContact(Long id) {
        mContactDataIds.remove(id);

        // Remove the group that contains this contact.
        boolean isUpdated = false;
        Iterator<ContactGroup> iterator = mGroups.values().iterator();
        while (iterator.hasNext()) {
            ContactGroup g = iterator.next();
            for (Long dataId : g.mDataIds) {
                if (dataId.equals(id)) {
                    isUpdated = true;
                    mContactGroupIds.remove(g.mGroupId);
                }
            }
        }

        // Update UI status.
        if (isUpdated) {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Obtains the contacts in the group.
     * 
     * @param groupId Group's ID
     * @param isBirthday Contact should has birthday.
     * @return A cursor for for accessing the contacts.
     */
    private Cursor getGroupPhoneCurosr(long groupId) {
        ContentResolver resolver = getActivity().getContentResolver();

        // Get contact's ID in the selected group.
        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                GroupMembership.GROUP_ROW_ID,
                GroupMembership.CONTACT_ID
        };

        String selection = GroupMembership.GROUP_ROW_ID + " = ?";
        String[] selectionArgs = new String[] {
                String.valueOf(groupId),
        };

        HashSet<Long> contactIds = new HashSet<Long>();
        Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    long contactId = cursor.getLong(cursor
                            .getColumnIndex(GroupMembership.CONTACT_ID));
                    contactIds.add(contactId);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        // Filter out contacts without birthday.
        if (mBirthdays != null) {
            Cursor event = ContactUtility.getEventCursor(resolver, contactIds);
            contactIds.clear();
            if (event != null) {
                if (event.moveToFirst()) {
                    do {
                        long contactId = event.getLong(event.getColumnIndex(Event.CONTACT_ID));
                        String birthday = event.getString(event.getColumnIndex(Event.START_DATE));
                        contactIds.add(contactId);
                        mBirthdays.put((int) contactId, birthday);
                    } while (event.moveToNext());
                }
                event.close();
            }
        }

        // No contacts is found.
        if (contactIds.size() <= 0) {
            return null;
        }

        // Get contact's information in the selected contacts.

        // Create SQL.
        String statement = "";
        for (Long id : contactIds) {
            statement += id + ",";
        }

        projection = new String[] {
                ContactsContract.Data._ID,
                Contacts.PHOTO_ID,
                Contacts.DISPLAY_NAME,
                // ContactsContract.Contacts.HAS_PHONE_NUMBER,
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
        };
        selection = ContactsContract.Data.MIMETYPE + " = ? AND "
                + Phone.CONTACT_ID + " IN ("
                + statement.substring(0, statement.length() - 1) + ")";
        selectionArgs = new String[] {
                Phone.CONTENT_ITEM_TYPE
        };
        String sortOrder = Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

        return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    public class MyExpandableListAdapter extends SimpleCursorTreeAdapter {
        static final String TAG = "MyExpandableListAdapter";

        private OnGroupClickListener mListener;

        public MyExpandableListAdapter(Context context, Cursor cursor,
                int groupLayout, int childLayout, String[] groupFrom,
                int[] groupTo, String[] childrenFrom, int[] childrenTo) {
            super(context, cursor, groupLayout, groupFrom, groupTo,
                    childLayout, childrenFrom, childrenTo);
        }

        public void setOnGroupClickListener(OnGroupClickListener l) {
            mListener = l;
        }

        @Override
        protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
            super.bindGroupView(view, context, cursor, isExpanded);

            final Long groupId = cursor.getLong(cursor
                    .getColumnIndex(ContactsContract.Groups._ID));
            Log.d(TAG, "bindGroupView:" + groupId + "=" + isExpanded);

            CheckBox cb = (CheckBox) view.findViewById(R.id.checkBoxAddGroup);
            if (cb != null) {
                // Set checked or not.
                cb.setChecked(mContactGroupIds.contains(groupId));

                // Set onChecked listener.
                cb.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (mListener != null) {
                            mListener.onGroupClick(null, v, 0, groupId);
                        }
                    }
                });
            }
        }

        @Override
        protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
            super.bindChildView(view, context, cursor, isLastChild);

            long id = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID));
            int contactId = cursor.getInt(cursor.getColumnIndex(Phone.CONTACT_ID));
            Log.d(TAG, "bindChildView:" + id);

            CheckBox cb = (CheckBox) view.findViewById(R.id.checkBoxAdd);
            if (cb != null) {
                // Set enabled or not.
                if (mBirthdays == null) {
                    cb.setEnabled(true);
                } else {
                    String birthday = mBirthdays.get(contactId);
                    if (birthday != null && birthday.length() > 0) {
                        cb.setEnabled(true);
                    } else {
                        cb.setEnabled(false);
                    }
                }

                // Set checked or not.
                cb.setChecked(mContactDataIds.contains(id));
            }
        }

        @Override
        protected Cursor getChildrenCursor(Cursor groupCursor) {
            // Get child cursor.
            long id = groupCursor.getLong(groupCursor
                    .getColumnIndexOrThrow(ContactsContract.Groups._ID));
            Cursor cursor = getGroupPhoneCurosr(id);

            // Add all contacts in the group.
            ContactGroup g = new ContactGroup();
            g.mGroupId = id;
            g.mDataIds = new ArrayList<Long>();

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        long dataId = cursor.getLong(cursor
                                .getColumnIndex(ContactsContract.Data._ID));
                        g.mDataIds.add(dataId);
                    } while (cursor.moveToNext());
                }
                // Should not close cursor here!
            }
            mGroups.put(id, g);

            return cursor;
        }
    }
}
