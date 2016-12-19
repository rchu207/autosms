
package tw.idv.rchu.autosms;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;


public class SmartElementController {
    static final String TAG = "SmartElementController";

    private ContentResolver mResolver;
    private DBExternalHelper mDbExternalHelper;
    private String[] mTagNames;
    private final CharSequence[] mTagValues = {
            "NAME", "FNAME", "GNAME", "NICKNAME", "NOTES", "APPA", "APPB", "APPC"
    };

    // Should be same as DBExternalHelper.BULK_RECORD_COLUMN_COUNT.
    private String[] mColumnNames;
    private final CharSequence[] mColumnValues = {
            "COLUMNB", "COLUMNC", "COLUMND", "COLUMNE", "COLUMNF", "COLUMNG"
    };
    
    private final static int NAME_TAG_INDEX = 0;
    private final static int FAMILY_NAME_TAG_INDEX = 1;
    private final static int GIVEN_NAME_TAG_INDEX = 2;
    private final static int NICKNAME_TAG_INDEX = 3;
    private final static int NOTES_TAG_INDEX = 4;
    private final static int APP_TAG_INDEX = 5;

    private final static int APP_TAG_COUNT = 3;

    public SmartElementController(ContentResolver resolver, Resources res) {
        this(resolver, null, res);
    }
    
    public SmartElementController(ContentResolver resolver, DBExternalHelper dbExternalHelper, Resources res) {
        mResolver = resolver;
        mDbExternalHelper = dbExternalHelper;
        mTagNames = res.getStringArray(R.array.tag_names);
        mColumnNames = res.getStringArray(R.array.column_names);        
    }

    public CharSequence getTagValue(int index) {
        if (index >= mTagValues.length) {
            return "";
        }

        return mTagValues[index];
    }

    public String[] getTags() {
        return mTagNames;
    }

    public CharSequence getColumnValue(int index) {
        if (index >= mColumnValues.length) {
            return "";
        }

        return mColumnValues[index];
    }
    
    public String[] getColumns() {
        return mColumnNames;
    }

    public String replaceAllElements(String contactId, String content) {
        String[] results = new String[mTagValues.length];

        // No selected contact.
        if (contactId == null) {
            return deleteAllElements(content);
        }

        // Run query to get names.
        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                StructuredName.DISPLAY_NAME,
                StructuredName.FAMILY_NAME,
                StructuredName.GIVEN_NAME,
        };
        String selection = ContactsContract.Data.MIMETYPE + " = ? AND "
                + StructuredName.CONTACT_ID + " = ?";
        String[] selectionArgs = new String[] {
                StructuredName.CONTENT_ITEM_TYPE,
                contactId
        };

        // Run query to get names.
        Cursor cursor = mResolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                results[NAME_TAG_INDEX] = cursor.getString(cursor
                        .getColumnIndex(StructuredName.DISPLAY_NAME));
                results[FAMILY_NAME_TAG_INDEX] = cursor.getString(cursor
                        .getColumnIndex(StructuredName.FAMILY_NAME));
                results[GIVEN_NAME_TAG_INDEX] = cursor.getString(cursor
                        .getColumnIndex(StructuredName.GIVEN_NAME));
            }
            cursor.close();
        }

        // Run query to get nickname.
        projection = new String[] {
                Nickname.NAME,
        };
        selection = ContactsContract.Data.MIMETYPE + " = ? AND "
                + Nickname.CONTACT_ID + " = ?";
        selectionArgs = new String[] {
                Nickname.CONTENT_ITEM_TYPE,
                contactId
        };
        cursor = mResolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                // TODO: Some people have multiple nicknames, only get first
                // one.
                do {
                    results[NICKNAME_TAG_INDEX] = cursor.getString(cursor
                            .getColumnIndex(Nickname.NAME));
                    if (null != results[NICKNAME_TAG_INDEX]) {
                        break;
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        // Run query to get notes.
        projection = new String[] {
                Note.NOTE,
        };
        selection = ContactsContract.Data.MIMETYPE + " = ? AND "
                + Note.CONTACT_ID + " = ?";
        selectionArgs = new String[] {
                Note.CONTENT_ITEM_TYPE,
                contactId
        };
        cursor = mResolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                results[NOTES_TAG_INDEX] = cursor.getString(cursor.getColumnIndex(Note.NOTE));
            }
            cursor.close();
        }

        // Run query to get iSend relations.
        projection = new String[] {
                Relation.NAME,
                Relation.TYPE,
                Relation.LABEL,
        };
        selection = ContactsContract.Data.MIMETYPE + " = ? AND "
                + Relation.CONTACT_ID + " = ?";
        selectionArgs = new String[] {
                Relation.CONTENT_ITEM_TYPE,
                contactId
        };
        cursor = mResolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    if (cursor.getInt(cursor.getColumnIndex(Relation.TYPE)) == Relation.TYPE_CUSTOM) {
                        String label = cursor.getString(cursor.getColumnIndex(Relation.LABEL));
                        for (int i = 0; i < APP_TAG_COUNT; i++) {
                            if (label.toUpperCase().equals(mTagValues[APP_TAG_INDEX + i])) {
                                results[APP_TAG_INDEX + i] = cursor.getString(cursor
                                        .getColumnIndex(Relation.NAME));
                                break;
                            }
                        }
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        }        

        // Replace smart elements from the selected contact.
        for (int i = 0; i < mTagValues.length; i++) {
            String tag = "[[" + mTagValues[i] + "]]";
            String value = results[i];
            if (value == null) {
                value = "";
            }

            int index = content.indexOf(tag);
            while (index >= 0) {
                content = content.replace(tag, value);
                index = content.indexOf(tag);
            }
        }

        return content;
    }

    public String deleteAllElements(String content) {
        // Replace smart elements by empty string.
        for (CharSequence element : mTagValues) {
            String tag = "[[" + element + "]]";
            content = content.replace(tag, "");
        }

        return content;
    }
    
    public String replaceAllColumns(String bulkFileId, String phoneDataId, String content) {
        // No bulk file or contact.
        if (bulkFileId == null || phoneDataId == null) {
            return deleteAllElements(content);
        }
        if (mDbExternalHelper == null) {
            return content;
        }
        
        // Get CSV column values.
        String[] results = mDbExternalHelper.getBulkRecordColumns(bulkFileId, phoneDataId);
        
        // Replace smart elements from the selected bulk file.
        for (int i = 0; i < mColumnValues.length; i++) {
            String tag = "[[" + mColumnValues[i] + "]]";
            String value = results[i];
            if (value == null) {
                value = "";
            }

            int index = content.indexOf(tag);
            while (index >= 0) {
                content = content.replace(tag, value);
                index = content.indexOf(tag);
            }
        }            

        return content;
    }    
}
