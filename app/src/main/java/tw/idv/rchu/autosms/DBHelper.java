
package tw.idv.rchu.autosms;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.util.Log;

import java.io.File;

public abstract class DBHelper {

    static final String TAG = "DatabaseHelper";
    static final String TEXT_TYPE = " TEXT";
    static final String INT_TYPE = " INTEGER";
    static final String COMMA_SEP = ",";
    
    protected final Context mContext;
    protected SQLiteDatabase mDatabase;

    private final int mVersion;
    private final File mFile;
    private boolean mIsInitializing = false;
    
    static final String DATABASE_NAME = "iSend.db";    

    public DBHelper(Context context, int version, String name, boolean internal) {
        mContext = context;
        mVersion = version;
        
        if (internal) {
            mFile = context.getDatabasePath(name);
        } else {
            File externalDir = new File(Environment.getExternalStorageDirectory(), "/Android/data/"
                    + mContext.getPackageName() + "/files");
            mFile = new File(externalDir, name);                
        }
    }

    protected void onCreate(SQLiteDatabase db) {
    }

    protected void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized boolean openWritable() {
        if ((mDatabase != null) && mDatabase.isOpen()) {
            // Database is already opened.
            return true;
        }
    
        if (mIsInitializing) {
            throw new IllegalStateException("getWritableDatabase called recursively");
        }
    
        // If we have a read-only database open, someone could be using it
        // (though they shouldn't), which would cause a lock to be held on
        // the file, and our attempts to open the database read-write would
        // fail waiting for the file lock. To prevent that, we acquire the
        // lock on the read-only database, which shuts out other users.
    
        boolean success = false;
        SQLiteDatabase db = null;
        try {
            mIsInitializing = true;
                        
            // Check parent directories exist.
            File parent = mFile.getParentFile();
            if (parent.getPath().startsWith(Environment.getExternalStorageDirectory().getPath())) {
                // Check external storage.
                String stage = Environment.getExternalStorageState();
                if (!stage.equals(Environment.MEDIA_MOUNTED)) {
                    throw new SQLiteException("Extrenal storage is not mounted.");
                }                
            }
            if (!parent.exists()) {
                parent.mkdirs();
            }
    
            // Open or create database.
            db = SQLiteDatabase.openOrCreateDatabase(mFile.getPath(), null);
    
            // Get database version.
            int version = db.getVersion();
            if (version != mVersion) {
                db.beginTransaction();
                try {
                    if (version == 0) {
                        onCreate(db);
                    } else {
                        onUpgrade(db, version, mVersion);
                    }
                    db.setVersion(mVersion);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
    
            // TODO: call onOpen();
            // onOpen(db);
            success = true;
        } catch (SQLiteException sqlException) {
            Log.e(TAG, sqlException.toString());
            success = false;
        }
    
        // Database is initialized.
        mIsInitializing = false;
    
        // Result the result.
        if (success) {
            if (mDatabase != null) {
                mDatabase.close();
            }
    
            mDatabase = db;
        } else {
            // Open new database is failed, release it.
            if (db != null) {
                db.close();
            }
        }
    
        return success;
    }

    public synchronized void closeWritable() {
        if (mDatabase != null) {
            mDatabase.close();
        }
    
        mDatabase = null;
    }

}
