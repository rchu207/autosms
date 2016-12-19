
package tw.idv.rchu.autosms;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.SparseIntArray;

import java.util.ArrayList;

public class DBExternalHelper extends DBHelper {
    static final String TAG = "DBExternalHelper";

    public static abstract class TemplateEntry implements BaseColumns {
        public static final String TABLE_NAME = "template";
        public static final String COLUMN_CONTENT = "t_content";
    }

    public static abstract class HistoryEntry implements BaseColumns {
        public static final String TABLE_NAME = "history";
        public static final String COLUMN_NAME = "h_name";
        public static final String COLUMN_RESULT = "h_result";
        public static final String COLUMN_DATETIME = "h_datetime";
    }

    public static abstract class HistoryResultEntry implements BaseColumns {
        public static final String TABLE_NAME = "history_result";
        public static final String COLUMN_HISTORY_ID = "hr_historyid";
        public static final String COLUMN_INDEX = "hr_index";
        public static final String COLUMN_RESULT = "hr_result";
        public static final String COLUMN_CONTACT = "hr_contact";
        public static final String COLUMN_ADDRESS = "hr_address";
        public static final String COLUMN_BODY = "hr_body";
    }
    
    public static abstract class BulkFileEntry implements BaseColumns {
        public static final String TABLE_NAME = "bulk_file";
        public static final String COLUMN_NAME = "bf_name";
        public static final String COLUMN_TIMESTAMP = "bf_timestamp";
    }    

    public static abstract class BulkRecordEntry implements BaseColumns {
        public static final String TABLE_NAME = "bulk_record";
        public static final String COLUMN_FILE_ID = "br_fileid";
        public static final String COLUMN_PHONE_DATA_ID = "br_phonedataid";
        public static final String COLUMN_TAGB = "br_tagb";
        public static final String COLUMN_TAGC = "br_tagc";
        public static final String COLUMN_TAGD = "br_tagd";
        public static final String COLUMN_TAGE = "br_tage";
        public static final String COLUMN_TAGF = "br_tagf";
        public static final String COLUMN_TAGG = "br_tagg";
    }
    
    public static abstract class HistoryResult {
        public static final int SUCCESS = 1;
        public static final int PENDING = 0;
        public static final int MANUALLY_STOP = -1;
        public static final int TIMEOUT = -2;
        public static final int SAFE_MODE = -3;
        public static final int ROAMING = -4;
        public static final int ERROR_GENERIC_FAILURE = -5;
        public static final int ERROR_NO_SERVICE = -6;
        public static final int ERROR_NULL_PDU = -7;
        public static final int ERROR_RADIO_OFF = -8;
    }

    static final String SQL_CREATE_TEMPLATE_ENTRIES =
            "CREATE TABLE " + TemplateEntry.TABLE_NAME + " (" +
                    TemplateEntry._ID + " INTEGER PRIMARY KEY, " +
                    TemplateEntry.COLUMN_CONTENT + TEXT_TYPE + " )";

    static final String SQL_CREATE_HISTORY_ENTRIES =
            "CREATE TABLE " + HistoryEntry.TABLE_NAME + " (" +
                    HistoryEntry._ID + " INTEGER PRIMARY KEY" + COMMA_SEP +
                    HistoryEntry.COLUMN_NAME + TEXT_TYPE + COMMA_SEP +
                    HistoryEntry.COLUMN_RESULT + INT_TYPE + COMMA_SEP +
                    HistoryEntry.COLUMN_DATETIME + TEXT_TYPE + " )";

    static final String SQL_CREATE_HISTORY_RESULT_ENTRIES =
            "CREATE TABLE " + HistoryResultEntry.TABLE_NAME + " (" +
                    HistoryResultEntry._ID + " INTEGER PRIMARY KEY" + COMMA_SEP +
                    HistoryResultEntry.COLUMN_HISTORY_ID + INT_TYPE + COMMA_SEP +
                    HistoryResultEntry.COLUMN_INDEX + INT_TYPE + COMMA_SEP +
                    HistoryResultEntry.COLUMN_RESULT + INT_TYPE + COMMA_SEP +
                    HistoryResultEntry.COLUMN_CONTACT + TEXT_TYPE + COMMA_SEP +
                    HistoryResultEntry.COLUMN_ADDRESS + TEXT_TYPE + COMMA_SEP +
                    HistoryResultEntry.COLUMN_BODY + TEXT_TYPE + " )";

    static final String SQL_CREATE_BULK_FILE_ENTRIES =
            "CREATE TABLE " + BulkFileEntry.TABLE_NAME + " (" +
                    BulkFileEntry._ID + " INTEGER PRIMARY KEY" + COMMA_SEP +
                    BulkFileEntry.COLUMN_NAME + TEXT_TYPE + COMMA_SEP +
                    BulkFileEntry.COLUMN_TIMESTAMP + TEXT_TYPE + " )";

    static final String SQL_CREATE_BULK_RECORD_ENTRIES =
            "CREATE TABLE " + BulkRecordEntry.TABLE_NAME + " (" +
                    BulkRecordEntry._ID + " INTEGER PRIMARY KEY" + COMMA_SEP +
                    BulkRecordEntry.COLUMN_FILE_ID + INT_TYPE + COMMA_SEP +
                    BulkRecordEntry.COLUMN_PHONE_DATA_ID + INT_TYPE + COMMA_SEP +
                    BulkRecordEntry.COLUMN_TAGB + TEXT_TYPE + COMMA_SEP +
                    BulkRecordEntry.COLUMN_TAGC + TEXT_TYPE + COMMA_SEP +
                    BulkRecordEntry.COLUMN_TAGD + TEXT_TYPE + COMMA_SEP +
                    BulkRecordEntry.COLUMN_TAGE + TEXT_TYPE + COMMA_SEP +
                    BulkRecordEntry.COLUMN_TAGF + TEXT_TYPE + COMMA_SEP +
                    BulkRecordEntry.COLUMN_TAGG + TEXT_TYPE + " )";
    
    static final String SQL_DELETE_TEMPLATE_ENTRIES =
            "DROP TABLE IF EXISTS " + TemplateEntry.TABLE_NAME;
    static final String SQL_DELETE_HISTORY_ENTRIES =
            "DROP TABLE IF EXISTS " + HistoryEntry.TABLE_NAME;
    static final String SQL_DELETE_HISTORY_RESULT_ENTRIES =
            "DROP TABLE IF EXISTS " + HistoryResultEntry.TABLE_NAME;

    // If you change the database schema, you must increment the database
    // version and modify onUpgrade().
    static final int DATABASE_VERSION = 2;

    public DBExternalHelper(Context context) {
        super(context, DATABASE_VERSION, DATABASE_NAME, false);
    }

    @Override
    protected void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Create external database.");
        db.execSQL(SQL_CREATE_TEMPLATE_ENTRIES);
        db.execSQL(SQL_CREATE_HISTORY_ENTRIES);
        db.execSQL(SQL_CREATE_HISTORY_RESULT_ENTRIES);
        db.execSQL(SQL_CREATE_BULK_FILE_ENTRIES);
        db.execSQL(SQL_CREATE_BULK_RECORD_ENTRIES);
    }

    @Override
    protected void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrade external database.");
        if (oldVersion <= 1) {
            db.execSQL(SQL_CREATE_BULK_FILE_ENTRIES);
            db.execSQL(SQL_CREATE_BULK_RECORD_ENTRIES);            
        }
    }

    public static Cursor getTemplateCursor(SQLiteDatabase db, String selection,
            String[] selectionArgs) {
        if (db == null) {
            return null;
        }

        // Query columns from the database.
        String[] projection = {
                TemplateEntry._ID,
                TemplateEntry.COLUMN_CONTENT,
        };

        // Query database.
        return db.query(TemplateEntry.TABLE_NAME, projection, selection, selectionArgs, null, null,
                null);
    }

    public Cursor getTemplateCursor() {
        return getTemplateCursor(mDatabase, null, null);
    }

    public Cursor getTemplateCursor(String selection, String[] selectionArgs) {
        return getTemplateCursor(mDatabase, selection, selectionArgs);
    }

    public void copyTemplates(SQLiteDatabase source) {
        if ((source == null) || (mDatabase == null)) {
            return;
        }

        Cursor cursor = getTemplateCursor(source, null, null);
        if (cursor != null) {
            mDatabase.beginTransaction();

            if (cursor.moveToFirst()) {
                ContentValues values = new ContentValues();
                do {
                    values.put(TemplateEntry.COLUMN_CONTENT,
                            cursor.getString(cursor.getColumnIndex(TemplateEntry.COLUMN_CONTENT)));
                    mDatabase.insert(TemplateEntry.TABLE_NAME, null, values);
                } while (cursor.moveToNext());
            }
            mDatabase.setTransactionSuccessful();

            mDatabase.endTransaction();
            cursor.close();
        }
    }

    public long updateTemplate(long id, ContentValues values) {
        if (mDatabase == null) {
            return -1;
        }

        if (id < 0) {
            Log.d(TAG, "Insert new template.");

            return mDatabase.insert(TemplateEntry.TABLE_NAME, null, values);
        } else {
            Log.d(TAG, "Update exist template.");

            // Which row to update, based on the ID
            String selection = TemplateEntry._ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(id)
            };

            return mDatabase.update(TemplateEntry.TABLE_NAME, values, selection, selectionArgs);
        }
    }

    public boolean deleteTemplate(long id) {
        if (mDatabase == null) {
            return false;
        }

        // Delete schedule.
        if (id < 0) {
            // Delete all schedule entries.
            mDatabase.delete(TemplateEntry.TABLE_NAME, null, null);
        } else {
            // Delete the selected schedule entry.
            String selection = TemplateEntry._ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(id)
            };
            mDatabase.delete(TemplateEntry.TABLE_NAME, selection, selectionArgs);
        }

        return true;
    }

    public static Cursor getHistoryCursor(SQLiteDatabase db, String selection, String[] selectionArgs) {
        if (db == null) {
            return null;
        }

        // Query columns from the database.
        String[] projection = {
                HistoryEntry._ID,
                HistoryEntry.COLUMN_NAME,
                HistoryEntry.COLUMN_RESULT,
                HistoryEntry.COLUMN_DATETIME,
        };

        // Sort in the resulting Cursor
        String sortOrder = HistoryEntry.COLUMN_DATETIME + " DESC, " + HistoryEntry._ID + " DESC";

        // Return database cursor.
        return db.query(HistoryEntry.TABLE_NAME, projection, selection, selectionArgs,
                null, null, sortOrder);
    }

    public Cursor getHistoryCursor() {
        // Return database cursor.
        return getHistoryCursor(-1);
    }

    public Cursor getHistoryCursor(long id) {
        // Make selection arguments.
        String selection = null;
        String[] selectionArgs = null;
        if (id >= 0) {
            selection = HistoryEntry._ID + " = ?";
            selectionArgs = new String[1];
            selectionArgs[0] = String.valueOf(id);
        }
        
        return getHistoryCursor(mDatabase, selection, selectionArgs);
    }

    public static Cursor getHistoryResultCursor(SQLiteDatabase db, String selection, String[] selectionArgs) {
        if (db == null) {
            return null;
        }

        // Query columns from the database.
        String[] projection = {
                HistoryResultEntry._ID,
                HistoryResultEntry.COLUMN_HISTORY_ID,
                HistoryResultEntry.COLUMN_INDEX,
                HistoryResultEntry.COLUMN_RESULT,
                HistoryResultEntry.COLUMN_CONTACT,
                HistoryResultEntry.COLUMN_ADDRESS,
                HistoryResultEntry.COLUMN_BODY,
        };

        // Return database cursor.
        return db.query(HistoryResultEntry.TABLE_NAME, projection, selection, selectionArgs,
                null, null, null);
    }

    public Cursor getHistoryResultCursor(long historyId) {
        // Make selection arguments.
        String selection = HistoryResultEntry.COLUMN_HISTORY_ID + " = ?";
        String[] selectionArgs = {
                String.valueOf(historyId)
        };
        
        return getHistoryResultCursor(mDatabase, selection, selectionArgs);
    }

    public Cursor getHistoryResultCursor(long historyId, int index) {
        // Make selection arguments.
        String selection = HistoryResultEntry.COLUMN_HISTORY_ID + " = ? AND "
                + HistoryResultEntry.COLUMN_INDEX + " = ?";
        String[] selectionArgs = {
                String.valueOf(historyId),
                String.valueOf(index),
        };
        
        return getHistoryResultCursor(mDatabase, selection, selectionArgs);
    }

    public long updateHistory(long id, ContentValues values) {
        if (mDatabase == null) {
            return -1;
        }

        if (id < 0) {
            Log.d(TAG, "Insert new history.");

            if (!values.containsKey(HistoryEntry.COLUMN_RESULT)) {
                values.put(HistoryEntry.COLUMN_RESULT, HistoryResult.PENDING);
            }
            long newId = mDatabase.insert(HistoryEntry.TABLE_NAME, null, values);
            return newId;
        } else {
            Log.d(TAG, "Update exist history.");

            // Which row to update, based on the ID
            String selection = HistoryEntry._ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(id)
            };

            return mDatabase.update(HistoryEntry.TABLE_NAME, values, selection, selectionArgs);
        }
    }

    public void copyHistory(SQLiteDatabase source) {
        if ((source == null) || (mDatabase == null)) {
            return;
        }

        Cursor cursor = getHistoryCursor(source, null, null);
        if (cursor != null) {
            mDatabase.beginTransaction();

            ContentValues values = new ContentValues();
            if (cursor.moveToFirst()) {
                do {
                    // Copy history.
                    values.put(HistoryEntry.COLUMN_NAME,
                            cursor.getString(cursor.getColumnIndex(HistoryEntry.COLUMN_NAME)));
                    values.put(HistoryEntry.COLUMN_DATETIME,
                            cursor.getString(cursor.getColumnIndex(HistoryEntry.COLUMN_DATETIME)));
                    values.put(HistoryEntry.COLUMN_RESULT,
                            cursor.getInt(cursor.getColumnIndex(HistoryEntry.COLUMN_RESULT)));
                    long historyId = mDatabase.insert(HistoryEntry.TABLE_NAME, null, values);

                    // Copy history result.
                    copyHistoryResult(source,
                            cursor.getLong(cursor.getColumnIndex(HistoryEntry._ID)), historyId);
                } while (cursor.moveToNext());
            }
            mDatabase.setTransactionSuccessful();

            mDatabase.endTransaction();
            cursor.close();
        }
    }

    private void copyHistoryResult(SQLiteDatabase source, long sourceId, long targetId) {
        if ((source == null) || (sourceId < 0) || (targetId < 0)) {
            return;
        }

        // Make selection arguments.
        String selection = HistoryResultEntry.COLUMN_HISTORY_ID + " = ?";
        String[] selectionArgs = {
                String.valueOf(sourceId)
        };
        
        Cursor cursor = getHistoryResultCursor(source, selection, selectionArgs);
        if (cursor != null) {
            ContentValues values = new ContentValues();
            if (cursor.moveToFirst()) {
                do {
                    values.put(HistoryResultEntry.COLUMN_HISTORY_ID,
                            targetId);
                    values.put(HistoryResultEntry.COLUMN_INDEX,
                            cursor.getInt(cursor.getColumnIndex(HistoryResultEntry.COLUMN_INDEX)));
                    values.put(HistoryResultEntry.COLUMN_RESULT,
                            cursor.getInt(cursor.getColumnIndex(HistoryResultEntry.COLUMN_RESULT)));
                    values.put(HistoryResultEntry.COLUMN_CONTACT,
                            cursor.getString(cursor
                                    .getColumnIndex(HistoryResultEntry.COLUMN_CONTACT)));
                    values.put(HistoryResultEntry.COLUMN_ADDRESS,
                            cursor.getString(cursor
                                    .getColumnIndex(HistoryResultEntry.COLUMN_ADDRESS)));
                    values.put(HistoryResultEntry.COLUMN_BODY,
                            cursor.getString(cursor.getColumnIndex(HistoryResultEntry.COLUMN_BODY)));

                    mDatabase.insert(HistoryResultEntry.TABLE_NAME, null, values);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    }

    public boolean insertHistoryResult(long historyId, int result, ArrayList<Intent> smsIntents) {
        if (mDatabase == null) {
            return false;
        } else if (historyId < 0) {
            return false;
        }

        Log.d(TAG, "Insert new history results with " + historyId);
        boolean dbResult = true;

        mDatabase.beginTransaction();
        for (Intent intent : smsIntents) {
            ContentValues values = new ContentValues();
            values.put(HistoryResultEntry.COLUMN_HISTORY_ID, historyId);
            values.put(HistoryResultEntry.COLUMN_INDEX, 0);
            values.put(HistoryResultEntry.COLUMN_RESULT, result);
            values.put(HistoryResultEntry.COLUMN_CONTACT,
                    intent.getStringExtra(SendSmsService.EXTRA_SMS_CONTACT));
            values.put(HistoryResultEntry.COLUMN_ADDRESS,
                    intent.getStringExtra(SendSmsService.EXTRA_SMS_ADDRESS));
            values.put(HistoryResultEntry.COLUMN_BODY,
                    intent.getStringExtra(SendSmsService.EXTRA_SMS_BODY));
            long id = mDatabase.insert(HistoryResultEntry.TABLE_NAME, null, values);
            if (id < 0) {
                dbResult = false;
            }
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();

        return dbResult;
    }

    public boolean updateHistoryResult(long historyId, int index, int result, Intent smsIntent) {
        if (mDatabase == null) {
            return false;
        } else if (historyId < 0) {
            return false;
        }

        // Query the existed history result.
        boolean isExisted = false;
        Cursor cursor = getHistoryResultCursor(historyId, index);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                isExisted = true;

                // Update the result to any errors.
                if (result == HistoryResult.SUCCESS) {
                    int previousResult = cursor.getInt(cursor
                            .getColumnIndex(HistoryResultEntry.COLUMN_RESULT));
                    result = previousResult;
                }
            }
            cursor.close();
        }

        // Create new values.
        boolean dbResult = true;
        ContentValues values = new ContentValues();
        values.put(HistoryResultEntry.COLUMN_RESULT, result);
        if (smsIntent != null) {
            values.put(HistoryResultEntry.COLUMN_CONTACT,
                    smsIntent.getStringExtra(SendSmsService.EXTRA_SMS_CONTACT));
            values.put(HistoryResultEntry.COLUMN_ADDRESS,
                    smsIntent.getStringExtra(SendSmsService.EXTRA_SMS_ADDRESS));
            values.put(HistoryResultEntry.COLUMN_BODY,
                    smsIntent.getStringExtra(SendSmsService.EXTRA_SMS_BODY));
        }

        if (isExisted) {
            Log.d(TAG, "Update previous history result with " + historyId + "," + index);

            // Make selection arguments.
            String selection = HistoryResultEntry.COLUMN_HISTORY_ID + " = ? AND "
                    + HistoryResultEntry.COLUMN_INDEX + " = ?";
            String[] selectionArgs = {
                    String.valueOf(historyId),
                    String.valueOf(index),
            };

            // Update previous history result.
            int rows = mDatabase.update(HistoryResultEntry.TABLE_NAME, values, selection,
                    selectionArgs);
            if (rows == 0) {
                dbResult = true;
            }
        } else {
            Log.d(TAG, "Insert new history result with " + historyId + "," + index);
            values.put(HistoryResultEntry.COLUMN_HISTORY_ID, historyId);
            values.put(HistoryResultEntry.COLUMN_INDEX, index);

            // Insert new history result.
            long id = mDatabase.insert(HistoryResultEntry.TABLE_NAME, null, values);
            if (id < 0) {
                dbResult = false;
            }
        }

        return dbResult;
    }

    public boolean deleteHistory(long id) {
        if (mDatabase == null) {
            return false;
        }

        // Delete history's result.
        deleteHistoryResult(id);

        // Delete history.
        if (id < 0) {
            // Delete all history entries.
            mDatabase.delete(HistoryEntry.TABLE_NAME, null, null);
        } else {
            // Delete the selected history entry.
            String selection = HistoryEntry._ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(id)
            };
            mDatabase.delete(HistoryEntry.TABLE_NAME, selection, selectionArgs);
        }

        return true;
    }

    private boolean deleteHistoryResult(long historyId) {
        if (mDatabase == null) {
            return false;
        }

        // Delete history's result.
        if (historyId < 0) {
            // Delete result entries of all history.
            mDatabase.delete(HistoryResultEntry.TABLE_NAME, null, null);
        } else {
            // Delete result entries of the selected history.
            String selection = HistoryResultEntry.COLUMN_HISTORY_ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(historyId)
            };
            mDatabase.delete(HistoryResultEntry.TABLE_NAME, selection, selectionArgs);
        }

        return true;
    }

    public int[] generateHistoryStatus(long id) {
        if (mDatabase == null) {
            return null;
        } else if (id < 0) {
            return null;
        }

        // Get history results.
        int totalCount = 0;
        SparseIntArray resultMap = new SparseIntArray();
        Cursor cursor = getHistoryResultCursor(id);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    int result = cursor.getInt(cursor
                            .getColumnIndex(HistoryResultEntry.COLUMN_RESULT));
                    resultMap.put(result, resultMap.get(result, 0) + 1);
                    totalCount++;
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        if (totalCount == 0) {
            return null;
        }

        // Get history's status.
        int status = HistoryResult.SUCCESS;
        cursor = getHistoryCursor(id);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                status = cursor.getInt(cursor.getColumnIndex(HistoryEntry.COLUMN_RESULT));
            }
            cursor.close();
        }

        // If status is PENDING, then generate the correct status.
        if (status == HistoryResult.PENDING) {
            // Set the most result as the final status.
            // But SUCCESS should be all results are SUCCESS.
            status = HistoryResult.SUCCESS;
            int maxCount = 0;
            for (int i = 0; i < resultMap.size(); i++) {
                int result = resultMap.keyAt(i);
                int value = resultMap.get(result);
                if ((result != HistoryResult.SUCCESS) && (value > maxCount)) {
                    status = result;
                    maxCount = value;
                }
            }

            // Update the final status to database.
            ContentValues values = new ContentValues();
            values.put(HistoryEntry.COLUMN_RESULT, status);
            updateHistory(id, values);
        }

        // Result success count and total count.
        int[] values = new int[2];
        values[0] = resultMap.get(HistoryResult.SUCCESS);
        values[1] = totalCount;
        return values;
    }
    
    public long updateBulkFile(long id, ContentValues values) {
        if (mDatabase == null) {
            return -1;
        }

        if (id < 0) {
            Log.d(TAG, "Insert new bulk file.");

            long newId = mDatabase.insert(BulkFileEntry.TABLE_NAME, null, values);
            return newId;
        } else {
            Log.d(TAG, "Update bulk file:" + id);

            // Which row to update, based on the ID
            String selection = BulkFileEntry._ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(id)
            };

            return mDatabase.update(BulkFileEntry.TABLE_NAME, values, selection, selectionArgs);
        }
    }    
    
    public boolean insertBulkRecord(long fileId, ArrayList<CsvFetcher.CsvRecord> records) {
        if (mDatabase == null) {
            return false;
        } else if (fileId < 0) {
            return false;
        }

        Log.d(TAG, "Insert new bulk records with " + fileId);
        boolean dbResult = true;

        int count = 0;
        mDatabase.beginTransaction();
        for (CsvFetcher.CsvRecord record : records) {
            if (record.dataId < 0 || record.tags.length <= 0) {
                continue;
            }

            ContentValues values = new ContentValues();
            values.put(BulkRecordEntry.COLUMN_FILE_ID, fileId);
            values.put(BulkRecordEntry.COLUMN_PHONE_DATA_ID, record.dataId);
            
            if (record.tags.length >= 1) {
                values.put(BulkRecordEntry.COLUMN_TAGB, record.tags[0]);
            }
            if (record.tags.length >= 2) {
                values.put(BulkRecordEntry.COLUMN_TAGC, record.tags[1]);
            }
            if (record.tags.length >= 3) {
                values.put(BulkRecordEntry.COLUMN_TAGD, record.tags[2]);
            }
            if (record.tags.length >= 4) {
                values.put(BulkRecordEntry.COLUMN_TAGE, record.tags[3]);
            }
            if (record.tags.length >= 5) {
                values.put(BulkRecordEntry.COLUMN_TAGF, record.tags[4]);
            }
            if (record.tags.length >= 6) {
                values.put(BulkRecordEntry.COLUMN_TAGG, record.tags[5]);
            }

            long id = mDatabase.insert(BulkRecordEntry.TABLE_NAME, null, values);
            if (id < 0) {
                dbResult = false;
                break;
            }
            
            count++;
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
        
        if (dbResult && count > 0) {
            return true;
        } else {
            return false;
        }
    }
    
    public boolean deleteBulkFile(long id) {
        if (mDatabase == null) {
            return false;
        } 

        Log.d(TAG, "Delete bulk file:" + id);
        
        if (id < 0) {
            mDatabase.delete(BulkRecordEntry.TABLE_NAME, null, null);    
            mDatabase.delete(BulkFileEntry.TABLE_NAME, null, null);    
        } else {
            // Delete the selected bulk records.
            {
                String selection = BulkRecordEntry.COLUMN_FILE_ID + " = ?";
                String[] selectionArgs = {
                        String.valueOf(id)
                };
                mDatabase.delete(BulkRecordEntry.TABLE_NAME, selection, selectionArgs);            
            }
            
            // Delete the selected bulk file.
            {
                String selection = BulkFileEntry._ID + " = ?";
                String[] selectionArgs = {
                        String.valueOf(id)
                };
                mDatabase.delete(BulkFileEntry.TABLE_NAME, selection, selectionArgs);            
            }            
        }

        return true;
    }    
    
    public String[] getBulkRecordColumns(String bulkFileId, String phoneDataId) {
        String[] projection = new String[] {
                BulkRecordEntry.COLUMN_TAGB,
                BulkRecordEntry.COLUMN_TAGC,
                BulkRecordEntry.COLUMN_TAGD,
                BulkRecordEntry.COLUMN_TAGE,
                BulkRecordEntry.COLUMN_TAGF,
                BulkRecordEntry.COLUMN_TAGG,
        };
        String selection = BulkRecordEntry.COLUMN_FILE_ID + " = ? AND "
                + BulkRecordEntry.COLUMN_PHONE_DATA_ID + " = ?";
        String[] selectionArgs = new String[] {
                bulkFileId,
                phoneDataId,
        };        
        
        String[] results = new String[CsvFetcher.MAX_TAG_COUNT];
        for (int i = 0; i < CsvFetcher.MAX_TAG_COUNT; i++) {
            results[i] = "";
        }
        
        Cursor cursor = mDatabase.query(BulkRecordEntry.TABLE_NAME, projection, selection, selectionArgs,
                null, null, null);        
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                for (int i = 0; i < CsvFetcher.MAX_TAG_COUNT; i++) {
                    results[i] = cursor.getString(cursor.getColumnIndex(projection[i]));
                }                    
            }
            cursor.close();
        }
        
        return results;
    }    
}
