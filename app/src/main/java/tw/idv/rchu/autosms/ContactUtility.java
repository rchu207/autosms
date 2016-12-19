
package tw.idv.rchu.autosms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.util.SparseArray;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactUtility {

    @SuppressWarnings("unused")
    private static final String TAG = "ContactUtility";

    public static final String COLUMN_BIRTHDAY = "isend_birthday";

    public static HashSet<Long> setupContactSpinner(Intent intent, String extraName,
            Activity activity, int resId) {
        Spinner spinner = (Spinner) activity.findViewById(resId);

        if (!intent.hasExtra(extraName)) {
            spinner.setEnabled(false);
            return null;
        }

        // Get contact phones.
        @SuppressWarnings("unchecked")
        HashSet<Long> dataIds = (HashSet<Long>) intent.getSerializableExtra(extraName);

        if (dataIds.isEmpty()) {
            spinner.setEnabled(false);
        } else {
            ContactSpinnerItem[] items = new ContactSpinnerItem[dataIds.size()];
            int i = 0;

            // Run query
            Cursor cursor = getPhoneCursor(activity.getContentResolver(), dataIds);
            if (cursor.moveToFirst()) {
                do {
                    items[i] = new ContactSpinnerItem(
                            cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID)),
                            cursor.getString(cursor.getColumnIndex(Phone.CONTACT_ID)),
                            cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME)),
                            cursor.getString(cursor.getColumnIndex(Phone.NUMBER)));
                    i++;
                } while (cursor.moveToNext());
            }
            cursor.close();

            // Show contacts' name on spinner.
            ArrayAdapter<ContactSpinnerItem> adapter = new ArrayAdapter<ContactSpinnerItem>(
                    activity,
                    android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);

            spinner.setEnabled(true);
        }

        return dataIds;
    }

    public static HashSet<Long> convertDataIdToContactId(ContentResolver resolver, String mimeType,
            Set<Long> dataIds) {
        HashSet<Long> contactIds = new HashSet<Long>();

        if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
            Cursor cursor = getPhoneCursor(resolver, dataIds);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        long contactId = cursor.getLong(cursor.getColumnIndex(Phone.CONTACT_ID));
                        contactIds.add(contactId);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }

        return contactIds;
    }

    public static Map<Long, List<Long>> getContactIdMapByDataId(ContentResolver resolver,
            String mimeType,
            Set<Long> dataIds) {
        HashMap<Long, List<Long>> contactIdMap = new HashMap<Long, List<Long>>();

        if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
            Cursor cursor = getPhoneCursor(resolver, dataIds);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        long dataId = cursor.getLong(cursor
                                .getColumnIndex(ContactsContract.Data._ID));
                        long contactId = cursor.getLong(cursor.getColumnIndex(Phone.CONTACT_ID));
                        if (contactIdMap.containsKey(contactId)) {
                            contactIdMap.get(contactId).add(dataId);
                        } else {
                            ArrayList<Long> array = new ArrayList<Long>(4);
                            array.add(dataId);
                            contactIdMap.put(contactId, array);
                        }
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }

        return contactIdMap;
    }

    /**
     * Obtains a DB cursor contains contact's name and phone.
     * 
     * @param dataIds Phone's data ID for phone number.
     * @return A DB cursor.
     */
    public static Cursor getPhoneCursor(ContentResolver resolver, Set<Long> dataIds) {
        if (resolver == null) {
            return null;
        }

        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.Data._ID,
                Contacts.PHOTO_ID,
                Contacts.DISPLAY_NAME,
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
        };

        if (dataIds == null) {
            // Get all phones.

            // Create SQL.
            String selection = ContactsContract.Data.MIMETYPE + "= ?";
            String[] selectionArgs = new String[] {
                    Phone.CONTENT_ITEM_TYPE
            };
            String sortOrder = Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

            // Run query
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        }

        // Get selected phones.
        if (dataIds.isEmpty()) {
            // No selected phones.
            return null;
        }

        // Create SQL.
        String statement = "";
        for (Long id : dataIds) {
            statement += id + ",";
        }
        String selection = ContactsContract.Data.MIMETYPE + "= ? AND "
                + ContactsContract.Data._ID + " IN ("
                + statement.substring(0, statement.length() - 1) + ")";
        String[] selectionArgs = new String[] {
                Phone.CONTENT_ITEM_TYPE
        };
        String sortOrder = Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

        // Run query
        return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    /**
     * Obtains a DB cursor contains contact's name and phone.
     * 
     * @param constraint Constraint value for name or phone number.
     * @return A DB cursor.
     */
    public static Cursor getPhoneCursor(ContentResolver resolver, String constraint) {
        if (resolver == null) {
            return null;
        }

        // Run query
        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.Data._ID,
                Contacts.PHOTO_ID,
                Contacts.DISPLAY_NAME,
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
        };
        String sortOrder = Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

        if (constraint.length() == 0) {
            // No constraint, get all phones.
            String selection = ContactsContract.Data.MIMETYPE + "= ?";
            String[] selectionArgs = new String[] {
                    Phone.CONTENT_ITEM_TYPE,
            };

            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } else {
            String selection = ContactsContract.Data.MIMETYPE + " = ? AND (" +
                    Contacts.DISPLAY_NAME + " LIKE ? OR " +
                    Phone.NUMBER + " LIKE ?)";
            String[] selectionArgs = new String[] {
                    Phone.CONTENT_ITEM_TYPE,
                    "%" + constraint + "%",
                    constraint + "%",
            };

            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        }
    }

    /**
     * Obtains a DB cursor contains contact's name and phone. And they should
     * have the birthday.
     * 
     * @param constraint Constraint value for name or phone number.
     * @return A DB cursor.
     */
    public static Cursor getPhoneCursorWithBirthday(ContentResolver resolver, String constraint) {
        if (resolver == null) {
            return null;
        }

        // Get all phones.
        Cursor phoneCursor = getPhoneCursor(resolver, constraint);
        if (phoneCursor == null) {
            return null;
        }
        // Get all birthdays.
        SparseArray<String> birthdays = getBirthdays(resolver);
        if (birthdays.size() == 0) {
            phoneCursor.close();
            return null;
        }

        // Create the result cursor.
        String[] matrixColumn = new String[] {
                ContactsContract.Data._ID,
                Contacts.PHOTO_ID,
                Contacts.DISPLAY_NAME,
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
                COLUMN_BIRTHDAY,
        };
        MatrixCursor cursor = new MatrixCursor(matrixColumn, 10);
        if (phoneCursor.moveToFirst()) {
            do {
                int contactId = phoneCursor.getInt(phoneCursor.getColumnIndex(Phone.CONTACT_ID));
                String birthday = birthdays.get(contactId);
                if (birthday != null) {
                    Object[] values = new Object[matrixColumn.length];
                    values[0] = phoneCursor.getLong(0);
                    values[1] = phoneCursor.getLong(1);
                    values[2] = phoneCursor.getString(2);

                    values[3] = contactId;
                    values[4] = phoneCursor.getString(4);
                    values[5] = phoneCursor.getLong(5);
                    values[6] = phoneCursor.getString(6);

                    values[7] = birthday;

                    cursor.addRow(values);
                }
            } while (phoneCursor.moveToNext());
        }
        phoneCursor.close();

        return cursor;
    }

    /**
     * Obtains a Phone cursor contains by contacts.
     * 
     * @param contactIds Contacts' IDs.
     * @return A DB cursor.
     */
    public static Cursor getPhoneCursorByContact(ContentResolver resolver, Set<Long> contactIds) {
        if (resolver == null) {
            return null;
        } else if ((contactIds == null) || (contactIds.size() == 0)) {
            return null;
        }

        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.Data._ID,
                Contacts.PHOTO_ID,
                Contacts.DISPLAY_NAME,
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
        };

        // Create SQL.
        String statement = "";
        for (Long id : contactIds) {
            statement += id + ",";
        }
        String selection = ContactsContract.Data.MIMETYPE + "= ? AND "
                + Phone.CONTACT_ID + " IN ("
                + statement.substring(0, statement.length() - 1) + ")";
        String[] selectionArgs = new String[] {
                Phone.CONTENT_ITEM_TYPE
        };
        String sortOrder = Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

        // Run query
        return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    /**
     * Obtains a DB cursor contains event's date.
     * 
     * @param resolver Activity's ContentResolver.
     * @param contactIds Contact's ID for events.
     * @return A DB cursor.
     */
    public static Cursor getEventCursor(ContentResolver resolver, Set<Long> contactIds) {
        if (resolver == null) {
            return null;
        }

        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.Data._ID,
                Contacts.DISPLAY_NAME,
                Event.CONTACT_ID,
                Event.START_DATE
        };

        if (contactIds == null) {
            // Get all contacts' birthday.

            // Create SQL.
            String selection = ContactsContract.Data.MIMETYPE + "= ? AND "
                    + Event.TYPE + "= ?";
            String[] selectionArgs = new String[] {
                    Event.CONTENT_ITEM_TYPE,
                    String.valueOf(Event.TYPE_BIRTHDAY)
            };
            String sortOrder = Event.START_DATE + " COLLATE LOCALIZED ASC";

            // Run query
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } else if (contactIds.isEmpty()) {
            // No selected contacts.
            return null;
        } else if (contactIds.size() == 1) {
            Iterator<Long> iterator = contactIds.iterator();
            if (iterator.hasNext()) {
                Long id = iterator.next();

                String selection = ContactsContract.Data.MIMETYPE + "= ? AND " +
                        Event.TYPE + "= ? AND " +
                        Event.CONTACT_ID + "= ?";
                String[] selectionArgs = new String[] {
                        Event.CONTENT_ITEM_TYPE,
                        String.valueOf(Event.TYPE_BIRTHDAY),
                        id.toString()
                };
                String sortOrder = Event.START_DATE + " COLLATE LOCALIZED ASC";

                return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
            } else {
                return null;
            }
        } else {
            // Create SQL.
            String statement = "";
            for (Long id : contactIds) {
                statement += id + ",";
            }

            String selection = ContactsContract.Data.MIMETYPE + "= ? AND "
                    + Event.TYPE + "= ? AND "
                    + Event.CONTACT_ID + " IN (" + statement.substring(0, statement.length() - 1)
                    + ")";
            String[] selectionArgs = new String[] {
                    Event.CONTENT_ITEM_TYPE,
                    String.valueOf(Event.TYPE_BIRTHDAY)
            };
            String sortOrder = Event.START_DATE + " COLLATE LOCALIZED ASC";

            // Run query
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        }
    }

    private static SparseArray<String> getBirthdays(ContentResolver resolver) {
        // Query birthday from database.
        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                Event.CONTACT_ID,
                Event.START_DATE,
        };
        String selection = ContactsContract.Data.MIMETYPE + "= ? AND "
                + Event.TYPE + "= ?";
        String[] selectionArgs = new String[] {
                Event.CONTENT_ITEM_TYPE,
                String.valueOf(Event.TYPE_BIRTHDAY)
        };
        Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);

        // Add birthdays.
        SparseArray<String> birthdays = new SparseArray<String>();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    birthdays.put(cursor.getInt(cursor.getColumnIndex(Event.CONTACT_ID)),
                            cursor.getString(cursor.getColumnIndex(Event.START_DATE)));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        return birthdays;
    }

    public static ArrayList<ContactBirthday> getBirthdays(ContentResolver resolver,
            Set<Long> contactIds) {
        ArrayList<ContactBirthday> cbList = new ArrayList<ContactBirthday>();

        // Query birthday from database.
        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                Contacts.DISPLAY_NAME,
                Event.CONTACT_ID,
                Event.START_DATE,
        };

        if (contactIds == null || contactIds.size() == 0) {
            String selection = ContactsContract.Data.MIMETYPE + "= ? AND "
                    + Event.TYPE + "= ?";
            String[] selectionArgs = new String[] {
                    Event.CONTENT_ITEM_TYPE,
                    String.valueOf(Event.TYPE_BIRTHDAY)
            };

            // Add birthdays.
            Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        ContactBirthday cb = new ContactBirthday();
                        cb.id = cursor.getInt(cursor.getColumnIndex(Event.CONTACT_ID));
                        cb.displayName = cursor.getString(cursor
                                .getColumnIndex(Contacts.DISPLAY_NAME));
                        cb.startDate = cursor.getString(cursor.getColumnIndex(Event.START_DATE));
                        if (cb.startDate.length() > 5)
                            cb.startDate = cb.startDate.substring(cb.startDate.length() - 5);
                        cbList.add(cb);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        } else {
            for (Long id : contactIds) {
                String selection = ContactsContract.Data.MIMETYPE + "= ? AND " +
                        Event.TYPE + "= ? AND " +
                        Event.CONTACT_ID + "= ?";
                String[] selectionArgs = new String[] {
                        Event.CONTENT_ITEM_TYPE,
                        String.valueOf(Event.TYPE_BIRTHDAY),
                        id.toString()
                };

                // Add birthdays.
                Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        do {
                            ContactBirthday cb = new ContactBirthday();
                            cb.id = cursor.getInt(cursor.getColumnIndex(Event.CONTACT_ID));
                            cb.displayName = cursor.getString(cursor
                                    .getColumnIndex(Contacts.DISPLAY_NAME));
                            cb.startDate = cursor
                                    .getString(cursor.getColumnIndex(Event.START_DATE));
                            if (cb.startDate.length() > 5)
                                cb.startDate = cb.startDate.substring(cb.startDate.length() - 5);
                            cbList.add(cb);
                        } while (cursor.moveToNext());
                    }
                    cursor.close();
                }
            }
        }

        Collections.sort(cbList, new Comparator<ContactBirthday>() {

            @Override
            public int compare(ContactBirthday lhs, ContactBirthday rhs) {
                return lhs.startDate.compareTo(rhs.startDate);
            }
        });

        return cbList;
    }

    /**
     * Obtains a DB cursor contains group that has contacts.
     * 
     * @param resolver Activity's ContentResolver.
     * @return A DB cursor.
     */
    public static Cursor getGroupCursor(ContentResolver resolver) {
        // Run query
        Uri uri = Groups.CONTENT_SUMMARY_URI;
        String[] projection = new String[] {
                Groups._ID,
                Groups.TITLE,
                Groups.SUMMARY_WITH_PHONES,
        };

        // Find groups that have at least one contact.
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        HashSet<Long> groupIds = new HashSet<Long>();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndex(Groups._ID));
                    int count = cursor.getInt(cursor
                            .getColumnIndex(Groups.SUMMARY_WITH_PHONES));
                    if (count > 0) {
                        groupIds.add(id);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        // Return the result cursor.
        if (groupIds.size() > 0) {
            // Create SQL.
            String statement = "";
            for (Long id : groupIds) {
                statement += id + ",";
            }
            String selection = Groups._ID + " IN ("
                    + statement.substring(0, statement.length() - 1) + ")";

            // Run query
            return resolver.query(uri, projection, selection, null, null);
        } else {
            return null;
        }
    }

    static public ArrayList<Intent> createSmsIntent(ContentResolver resolver,
            SmartElementController smartElement,
            Set<Long> dataIds, String content, long bulkFileId) {
        // Check input parameter.
        if ((dataIds == null) || (dataIds.size() == 0)) {
            return null;
        }

        // Create SQL.
        String[] projection = new String[] {
                ContactsContract.Data._ID,
                Contacts.DISPLAY_NAME,
                Phone.CONTACT_ID,
                Phone.NUMBER,
        };

        StringBuilder sb = new StringBuilder(256);
        for (Long id : dataIds) {
            sb.append(id);
            sb.append(",");
        }
        String selection = ContactsContract.Data.MIMETYPE + "= ? AND "
                + ContactsContract.Data._ID + " IN (" + sb.substring(0, sb.length() - 1) + ")";
        String[] selectionArgs = new String[] {
                Phone.CONTENT_ITEM_TYPE
        };

        // Create SMS sending intents.
        ArrayList<Intent> intents = new ArrayList<Intent>();

        // Run SQL query
        Cursor cursor = resolver.query(ContactsContract.Data.CONTENT_URI, projection,
                selection, selectionArgs, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    // Replace smart elements from the selected contact.
                    String contactId = cursor.getString(cursor.getColumnIndex(Phone.CONTACT_ID));                    
                    String phoneDataId = cursor.getString(cursor.getColumnIndex(ContactsContract.Data._ID));
                    String body;
                    if (smartElement != null) {
                        body = smartElement.replaceAllElements(contactId, content);
                        if (bulkFileId >= 0) {
                            body = smartElement.replaceAllColumns(String.valueOf(bulkFileId), phoneDataId, body);
                        }
                    } else {
                        body = content;
                    }

                    if (body.length() > 0) {
                        Intent intent = new Intent(Intent.ACTION_SENDTO);
                        intent.putExtra(SendSmsService.EXTRA_SMS_CONTACT,
                                cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME)));
                        intent.putExtra(SendSmsService.EXTRA_SMS_ADDRESS,
                                cursor.getString(cursor.getColumnIndex(Phone.NUMBER)));
                        intent.putExtra(SendSmsService.EXTRA_SMS_BODY, body);
                        intents.add(intent);
                    }
                } while (cursor.moveToNext());
            }

            // Close cursor.
            cursor.close();
        }

        if (intents.isEmpty()) {
            return null;
        } else {
            return intents;
        }
    }

    @SuppressLint("SimpleDateFormat")
    static public Set<Long> filterByBirthday(ContentResolver resolver, Set<Long> dataIds) {
        // Check input parameter.
        if ((dataIds == null) || (dataIds.size() == 0)) {
            return null;
        }

        Calendar now = Calendar.getInstance();

        // Get contacts' event cursor.
        Map<Long, List<Long>> contactIdMap = getContactIdMapByDataId(resolver,
                Phone.CONTENT_ITEM_TYPE, dataIds);
        Cursor cursor = getEventCursor(resolver, contactIdMap.keySet());
        if (cursor == null) {
            return null;
        }

        HashSet<Long> filterDataIds = new HashSet<Long>(32);
        if (cursor.moveToFirst()) {
            int contactIdIndex = cursor.getColumnIndex(Event.CONTACT_ID);
            int dateIndex = cursor.getColumnIndex(Event.START_DATE);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf2 = new SimpleDateFormat("--MM-dd");

            do {
                try {
                    Date d = sdf.parse(cursor.getString(dateIndex));
                    Calendar c = Calendar.getInstance();
                    c.setTime(d);
                    if ((c.get(Calendar.MONTH) == now.get(Calendar.MONTH))
                            && (c.get(Calendar.DATE) == now.get(Calendar.DATE))) {
                        List<Long> newDataIds = contactIdMap.get(cursor.getLong(contactIdIndex));
                        if (newDataIds != null) {
                            filterDataIds.addAll(newDataIds);
                        }
                    }
                } catch (ParseException e) {
                    try {
                        Date d = sdf2.parse(cursor.getString(dateIndex));
                        Calendar c = Calendar.getInstance();
                        c.setTime(d);
                        if ((c.get(Calendar.MONTH) == now.get(Calendar.MONTH))
                                && (c.get(Calendar.DATE) == now.get(Calendar.DATE))) {
                            List<Long> newDataIds = contactIdMap
                                    .get(cursor.getLong(contactIdIndex));
                            if (newDataIds != null) {
                                filterDataIds.addAll(newDataIds);
                            }
                        }
                    } catch (ParseException e1) {
                        // If parsing is failed, the skip this contact.
                    }
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        return filterDataIds;
    }

    @SuppressLint("SimpleDateFormat")
    public static List<Calendar> getContactBirthdayCalendar(ContentResolver resolver,
            Set<Long> dataIds, Calendar time) {
        Set<Long> contactIds = convertDataIdToContactId(resolver, Phone.CONTENT_ITEM_TYPE,
                dataIds);
        Cursor cursor = getEventCursor(resolver, contactIds);
        if (cursor == null) {
            return null;
        }

        ArrayList<Calendar> birthdays = new ArrayList<Calendar>();
        if (cursor.moveToFirst()) {
            int dateIndex = cursor.getColumnIndex(Event.START_DATE);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf2 = new SimpleDateFormat("--MM-dd");
            Calendar now = Calendar.getInstance();

            do {
                try {
                    Date d = sdf.parse(cursor.getString(dateIndex));
                    Calendar c = Calendar.getInstance();
                    c.setTime(d);
                    c.set(now.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE),
                            time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), 0);
                    birthdays.add(c);
                } catch (ParseException e) {
                    try {
                        Date d = sdf2.parse(cursor.getString(dateIndex));
                        Calendar c = Calendar.getInstance();
                        c.setTime(d);
                        c.set(now.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE),
                                time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), 0);
                        birthdays.add(c);
                    } catch (ParseException e1) {
                        // If parsing is failed, the skip this contact.
                    }
                }
            } while (cursor.moveToNext());

            // Sort birthdays.
            Collections.sort(birthdays);
        }
        cursor.close();

        return birthdays;
    }
}
