
package tw.idv.rchu.autosms;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.util.Log;

import java.text.SimpleDateFormat;

public class DBInternalHelper extends DBHelper {
    static final String TAG = "DBInternalHelper";
 
    public static abstract class ScheduleEntry implements BaseColumns {
        public static final String TABLE_NAME = "schedule";
        public static final String COLUMN_NAME = "s_name";
        public static final String COLUMN_STATUS = "s_status";
        public static final String COLUMN_REPEAT = "s_repeat";
        public static final String COLUMN_DATETIME = "s_datetime";
        public static final String COLUMN_PHONE_DATA_IDS = "s_phone_data_ids";
        public static final String COLUMN_SMS_CONTENT = "s_sms_content";
    }

    public static abstract class ScheduleRepeat {
        public static final int NONE = 0;
        public static final int DAILY = 1;
        public static final int WEEKLY = 2;
        public static final int MONTHLY = 3;
        public static final int YEARLY = 4;
        public static final int BIRTHDAY = 100;
        public static final int BULK = 200;
    }

    static final String SQL_CREATE_SCHEDULE_ENTRIES =
            "CREATE TABLE " + ScheduleEntry.TABLE_NAME + " (" +
                    ScheduleEntry._ID + " INTEGER PRIMARY KEY" + COMMA_SEP +
                    ScheduleEntry.COLUMN_NAME + TEXT_TYPE + COMMA_SEP +
                    ScheduleEntry.COLUMN_STATUS + INT_TYPE + COMMA_SEP +
                    ScheduleEntry.COLUMN_REPEAT + INT_TYPE + COMMA_SEP +
                    ScheduleEntry.COLUMN_DATETIME + TEXT_TYPE + COMMA_SEP +
                    ScheduleEntry.COLUMN_PHONE_DATA_IDS + TEXT_TYPE + COMMA_SEP +
                    ScheduleEntry.COLUMN_SMS_CONTENT + TEXT_TYPE + " )";

    static final String SQL_DELETE_SCHEDULE_ENTRIES =
            "DROP TABLE IF EXISTS " + ScheduleEntry.TABLE_NAME;

    // If you change the database schema, you must increment the database
    // version and modify onUpgrade().
    static final int DATABASE_VERSION = 3;

    public DBInternalHelper(Context context) {
        super(context, DATABASE_VERSION, DATABASE_NAME, true);
    }
    
    @Override
    protected void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_SCHEDULE_ENTRIES);
    }

    @Override
    protected void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrade internal database."); 
        if (oldVersion <= 2) {
            // Copy template and history to external database.
            DBExternalHelper dbHelper2 = new DBExternalHelper(mContext);
            dbHelper2.openWritable();
            dbHelper2.copyTemplates(db);
            dbHelper2.copyHistory(db);
            dbHelper2.closeWritable();
            
            // Delete template and history.
            db.execSQL(DBExternalHelper.SQL_DELETE_TEMPLATE_ENTRIES);
            db.execSQL(DBExternalHelper.SQL_DELETE_HISTORY_ENTRIES);
            db.execSQL(DBExternalHelper.SQL_DELETE_HISTORY_RESULT_ENTRIES);
        }
    }    

    @SuppressLint("SimpleDateFormat")
    public static SimpleDateFormat getScheduleDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm");
    }

    public Cursor getScheduleCursor(String selection, String[] selectionArgs) {
        if (mDatabase == null) {
            return null;
        }

        // Query columns from the database.
        String[] projection = {
                ScheduleEntry._ID,
                ScheduleEntry.COLUMN_NAME,
                ScheduleEntry.COLUMN_STATUS,
                ScheduleEntry.COLUMN_REPEAT,
                ScheduleEntry.COLUMN_DATETIME,
                ScheduleEntry.COLUMN_PHONE_DATA_IDS,
                ScheduleEntry.COLUMN_SMS_CONTENT,
        };

        // Sort in the resulting Cursor
        String sortOrder = ScheduleEntry.COLUMN_DATETIME + " ASC";

        // Query database.
        return mDatabase.query(ScheduleEntry.TABLE_NAME, projection, selection, selectionArgs,
                null, null, sortOrder);
    }

    public long updateSchedule(long id, ContentValues values) {
        if (mDatabase == null) {
            return -1;
        }

        if (id < 0) {
            Log.d(TAG, "Insert new schedule.");

            return mDatabase.insert(ScheduleEntry.TABLE_NAME, null, values);
        } else {
            Log.d(TAG, "Update exist schedule.");

            // Which row to update, based on the ID
            String selection = ScheduleEntry._ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(id)
            };

            return mDatabase.update(ScheduleEntry.TABLE_NAME, values, selection, selectionArgs);
        }
    }

    public boolean deleteSchdule(long id) {
        if (mDatabase == null) {
            return false;
        }

        // Delete schedule.
        if (id < 0) {
            // Delete all schedule entries.
            mDatabase.delete(ScheduleEntry.TABLE_NAME, null, null);
        } else {
            // Delete the selected schedule entry.
            String selection = ScheduleEntry._ID + " = ?";
            String[] selectionArgs = {
                    String.valueOf(id)
            };
            mDatabase.delete(ScheduleEntry.TABLE_NAME, selection, selectionArgs);
        }

        return true;
    }
}
