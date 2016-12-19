
package tw.idv.rchu.autosms;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import tw.idv.rchu.autosms.DBInternalHelper.ScheduleEntry;
import tw.idv.rchu.autosms.DBInternalHelper.ScheduleRepeat;
import tw.idv.rchu.autosms.util.AsyncTask;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

public class ListScheduleActivity extends ActionBarActivity implements
        SimpleCursorAdapter.ViewBinder {
    private static final String TAG = "[iSend]ListSchedule";
    private static final int EDIT_SCHEDULE = 1;

    private Context mContext;
    private Handler mUiHandler;
    private ListView mListView;
    private ProgressDialog mProgressDialog;
    private AlertDialog mAlertDialog;

    private DBInternalHelper mDbHelper;
    private DBExternalHelper mDbExternalHelper;
    private SimpleDateFormat mScheduleDateFormat;
    private DateFormat mDatetimeDateFormat;
    private SimpleDateFormat mWeekDateFormat;
    private SimpleDateFormat mBirthdayFormat;
    private String[] mRepeatTypes;

    private int mScheduleLimit;

    /**
     * Receiver for messages sent from MainService .
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Schedules are updated by MainService.");

            // Update UI status.
            mUiHandler.post(new Runnable() {

                @Override
                public void run() {
                    SimpleCursorAdapter adapter = (SimpleCursorAdapter) mListView.getAdapter();
                    if (adapter != null) {
                        adapter.getCursor().requery();
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };

    private AdapterView.OnItemClickListener mListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Launch EditScheduleActivity.
            startEditScheduleActivity(position, id);
        }
    };

    /**
     * The actual AsyncTask that will asynchronously open CSV file and parse it.
     */
    private class CsvImportWorkerTask extends AsyncTask<Object, Void, ArrayList<Long>> {
        /**
         * Background processing.
         */
        @Override
        protected ArrayList<Long> doInBackground(Object... params) {
            ArrayList<Long> results = new ArrayList<Long>(32);

            // Open and parse CSV file.
            File file = (File) params[0];
            CsvFetcher fetcher = new CsvFetcher();
            fetcher.parse(file);
            if (fetcher.getCount() <= 0) {
                results.add(0, (long) -1);
                return results;
            }

            // Get phone cursor.
            Cursor cursor = ContactUtility.getPhoneCursor(getContentResolver(), "");

            // Find matched contacts.
            for (int i = 0; i < fetcher.getCount(); i++) {
                CsvFetcher.CsvRecord record = fetcher.getItem(i);

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        do {
                            String phone = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                            if (PhoneNumberUtils.compare(mContext, phone, record.phone)) {
                                record.dataId = cursor.getLong(cursor
                                        .getColumnIndex(ContactsContract.Data._ID));
                                results.add(record.dataId);
                                break;
                            }
                        } while (cursor.moveToNext());
                    }
                }
            }
            if (cursor != null) {
                cursor.close();
            }

            // If no matched contacts, then return failed.
            if (results.size() <= 0) {
                results.add(0, (long) -1);
                return results;
            }

            // Insert customized iSend tags into database.
            ContentValues values = new ContentValues();
            values.put(DBExternalHelper.BulkFileEntry.COLUMN_NAME, file.getAbsolutePath());
            values.put(DBExternalHelper.BulkFileEntry.COLUMN_TIMESTAMP, file.lastModified());
            long bulkId = mDbExternalHelper.updateBulkFile(-1, values);
            mDbExternalHelper.insertBulkRecord(bulkId, fetcher.getItems());

            // Return bulk id.
            results.add(0, bulkId);
            return results;
        }

        /**
         * Once the image is processed, associates it to the imageView
         */
        @Override
        protected void onPostExecute(ArrayList<Long> results) {
            long bulkFileId = results.get(0);
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            if (isCancelled()) {
                Log.w(TAG, "Importing CSV is cancelled.");
                if (bulkFileId >= 0) {
                    mDbExternalHelper.deleteBulkFile(bulkFileId);
                }
                return;
            }

            if (bulkFileId >= 0) {
                // Check schedule limit.
                if (mListView.getCount() < mScheduleLimit) {
                    Intent intent = new Intent(mContext, EditScheduleActivity.class);
                    intent.setAction(Intent.ACTION_EDIT);
                    intent.putExtra(EditScheduleActivity.EXTRA_REPEAT_TYPE,
                            EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BULK);
                    intent.putExtra(EditScheduleActivity.EXTRA_BULK_FILE_ID, bulkFileId);

                    HashSet<Long> contactDataIds = new HashSet<Long>();
                    for (int i = 1; i < results.size(); i++) {
                        contactDataIds.add(results.get(i));
                    }
                    intent.putExtra(EditScheduleActivity.EXTRA_CONTACT_DATA_IDS, contactDataIds);
                    startActivityForResult(intent, EDIT_SCHEDULE);
                } else {
                    Log.w(TAG, "Reach schedule limit.");
                    mDbExternalHelper.deleteBulkFile(bulkFileId);
                    Toast.makeText(mContext, getString(R.string.msg_reach_schedule_limit),
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Importing CSV file is failed.");

                // Show alert dialog to notify CSV import is failed.
                if (mAlertDialog != null) {
                    mAlertDialog.dismiss();
                }
                mAlertDialog = new AlertDialog.Builder(mContext)
                        .setMessage(R.string.msg_import_csv_failed)
                        .setNeutralButton(android.R.string.ok, null).create();
                mAlertDialog.show();
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_schedule);

        mContext = this;
        mUiHandler = new Handler();

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(mListener);

        // TODO: use AsyncTask to open database.
        mDbHelper = new DBInternalHelper(this);
        mDbHelper.openWritable();
        mScheduleDateFormat = DBInternalHelper.getScheduleDateFormat();
        mDbExternalHelper = new DBExternalHelper(this);
        mDbExternalHelper.openWritable();

        mDatetimeDateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        mWeekDateFormat = new SimpleDateFormat("EEEE");
        mBirthdayFormat = new SimpleDateFormat("MM-dd");

        mRepeatTypes = getResources().getStringArray(R.array.schedule_repeat_types);
        mRepeatTypes[EditScheduleActivity.SCHEDULE_REPEAT_TYPE_NONE] = getString(R.string.schedule_timeout);

        // Build adapter with schedules.
        mListView.setAdapter(createSimpleCursorAdapter());

        // Register context menu.
        registerForContextMenu(mListView);

        // Setup action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Get schedule limit for Premium level.
        updateLimit(true);

        // Message handling - from MainService.
        final IntentFilter myFilter = new
                IntentFilter(MainService.BROADCAST_ACTION_SCHEDULE_UPDATED);
        registerReceiver(mReceiver, myFilter);

        // Import the CSV file.
        if (getIntent().getAction().equals(Intent.ACTION_INSERT)) {
            // Launch a thread to import CSV file.
            File file = new File(getIntent().getData().getPath());
            String message = String.format(getString(R.string.msg_import_csv), file.getName());
            mProgressDialog = ProgressDialog.show(this, "", message);

            CsvImportWorkerTask task = new CsvImportWorkerTask();
            task.execute(file);
        } else if (getIntent().getAction().equals(Intent.ACTION_SEND)) {
            // Get the SOS schedule.
            String selection = ScheduleEntry.COLUMN_REPEAT + " = ?";
            String[] selectionArgs = {
                    String.valueOf(EditScheduleActivity.SCHEDULE_REPEAT_TYPE_SOS),
            };

            long id = -1;
            long status = -1;
            Cursor cursor = mDbHelper.getScheduleCursor(selection, selectionArgs);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    id = cursor.getLong(cursor.getColumnIndex(ScheduleEntry._ID));
                    status = cursor.getLong(cursor.getColumnIndex(ScheduleEntry.COLUMN_STATUS));
                }
            }
            
            if (id >= 0 && status == EditScheduleActivity.SCHEDULE_STATUS_ON) {
                // If sos schedule exists, then send it directly.
                Log.i(TAG, "Send SOS right now.");
                startMainService(id);
            } else if (id >= 0) {
                // Modify the sos schedule.
                Log.d(TAG, "Modify SOS schedule.");
                startEditScheduleActivity(cursor, id);                
            } else {
                // Add a sos schedule.
                Log.d(TAG, "Add SOS schedule.");
                startAddScheduleActivity(EditScheduleActivity.SCHEDULE_REPEAT_TYPE_SOS);                
            }
            cursor.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_list_schedule, menu);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if ((item.getItemId() == R.id.itemNewTimeSchedule)
                    || (item.getItemId() == R.id.itemNewBirthdaySchedule)) {
                MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
            }
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mListView.getCount() > 0) {
            menu.findItem(R.id.itemActiveAllSchedule).setEnabled(true);
            menu.findItem(R.id.itemDeactiveAllSchedule).setEnabled(true);
            menu.findItem(R.id.itemDeleteAll).setEnabled(true);
        } else {
            menu.findItem(R.id.itemActiveAllSchedule).setEnabled(false);
            menu.findItem(R.id.itemDeactiveAllSchedule).setEnabled(false);
            menu.findItem(R.id.itemDeleteAll).setEnabled(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.activity_list_schedule_context, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.itemNewTimeSchedule:
            case R.id.itemNewBirthdaySchedule:
                if (mListView.getCount() < mScheduleLimit) {
                    if (item.getItemId() == R.id.itemNewBirthdaySchedule) {
                        // Start EditScheduleActivity to add a new birthday
                        // schedule.
                        startAddScheduleActivity(EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BIRTHDAY);
                    } else {
                        // Start EditScheduleActivity to add a new time
                        // schedule.
                        startAddScheduleActivity(EditScheduleActivity.SCHEDULE_REPEAT_TYPE_NONE);
                    }
                } else if (mScheduleLimit >= 100) {
                    Toast.makeText(this, getString(R.string.msg_reach_schedule_limit),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.itemActiveAllSchedule:
                modifyScheduleStatus(-1, 1);
                return true;
            case R.id.itemDeactiveAllSchedule:
                modifyScheduleStatus(-1, 0);
                return true;
            case R.id.itemDeleteAll:
                // Show confirm dialog to delete all schedule.
                if (mAlertDialog != null) {
                    mAlertDialog.dismiss();
                }
                mAlertDialog = new AlertDialog.Builder(this)
                        .setTitle("")
                        .setMessage(getString(R.string.msg_delete_all_schedules))
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        deleteSchedule(-1);
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null).create();
                mAlertDialog.show();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.itemActiveSchedule:
                modifyScheduleStatus(info.id, 1);
                break;
            case R.id.itemDeactiveSchedule:
                modifyScheduleStatus(info.id, 0);
                break;
            case R.id.itemSendNow:
                // Launch MainService.
                startMainService(info.id);
                break;
            case R.id.itemEdit:
                // Launch EditScheduleActivity.
                startEditScheduleActivity(info.position, info.id);
                break;
            case R.id.itemDelete:
                deleteSchedule(info.id);
                break;
            default:
                return super.onContextItemSelected(item);
        }

        return true;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");

        // Un-register message handling from MainService.
        unregisterReceiver(mReceiver);

        // Close database.
        mDbHelper.closeWritable();
        mDbExternalHelper.closeWritable();

        super.onDestroy();
    }

    private void startAddScheduleActivity(int repeatType) {
        Intent intent = new Intent(this, EditScheduleActivity.class);
        intent.setAction(Intent.ACTION_EDIT);
        intent.putExtra(EditScheduleActivity.EXTRA_REPEAT_TYPE, repeatType);
        startActivityForResult(intent, EDIT_SCHEDULE);
    }

    private void startEditScheduleActivity(int position, long id) {
        Cursor cursor = (Cursor) mListView.getAdapter().getItem(position);
        if (cursor == null)
            return;

        startEditScheduleActivity(cursor, id);
    }
    
    private void startEditScheduleActivity(Cursor cursor, long id) {
        // Prepare EditScheduleActivity intent to edit the schedule.
        Intent intent = new Intent(this, EditScheduleActivity.class);
        intent.setAction(Intent.ACTION_EDIT);

        // Put schedule id.
        intent.putExtra(EditScheduleActivity.EXTRA_SCHEDULE_ID, id);

        // Put schedule name and status.
        String name = cursor.getString(cursor.getColumnIndex(ScheduleEntry.COLUMN_NAME));
        intent.putExtra(EditScheduleActivity.EXTRA_SCHEDULE_NAME, (CharSequence) name);
        long status = cursor.getLong(cursor.getColumnIndex(ScheduleEntry.COLUMN_STATUS));
        intent.putExtra(EditScheduleActivity.EXTRA_SCHEDULE_STATUS, status);

        // Put repeat type and weekly repeat type.
        int repeat = cursor.getInt(cursor.getColumnIndex(ScheduleEntry.COLUMN_REPEAT));
        intent.putExtra(EditScheduleActivity.EXTRA_REPEAT_TYPE, repeat);

        // Put repeat date and time.
        String datetime = cursor.getString(cursor
                .getColumnIndex(ScheduleEntry.COLUMN_DATETIME));
        intent.putExtra(EditScheduleActivity.EXTRA_REPEAT_DATETIME, datetime);

        // Put bulk file ID and phone data IDs.
        String contactIdStr = cursor.getString(cursor
                .getColumnIndex(ScheduleEntry.COLUMN_PHONE_DATA_IDS));
        String[] contactIdStrs = contactIdStr.split(",");
        if (contactIdStrs != null && contactIdStrs.length > 0) {
            HashSet<Long> phoneDataIds = new HashSet<Long>();
            if (repeat == EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BULK) {
                intent.putExtra(EditScheduleActivity.EXTRA_BULK_FILE_ID,
                        Long.parseLong(contactIdStrs[0]));
                for (int i = 1; i < contactIdStrs.length; i++) {
                    phoneDataIds.add(Long.parseLong(contactIdStrs[i]));
                }
            } else {
                for (String idStr : contactIdStrs) {
                    phoneDataIds.add(Long.parseLong(idStr));
                }
            }
            intent.putExtra(EditScheduleActivity.EXTRA_CONTACT_DATA_IDS, phoneDataIds);
        }

        // Put the SMS content.
        String content = cursor.getString(cursor
                .getColumnIndex(ScheduleEntry.COLUMN_SMS_CONTENT));
        intent.putExtra(EditScheduleActivity.EXTRA_SMS_CONTENT, (CharSequence) content);

        // Launch EditScheduleActivity.
        startActivityForResult(intent, EDIT_SCHEDULE);
    }

    private void startMainService(long id) {
        if (id < 0) {
            return;
        }

        ArrayList<Long> scheduleIds = new ArrayList<Long>(1);
        scheduleIds.add(id);

        // Send SMS right now.
        Intent intent = new Intent(this, MainService.class);
        intent.setAction(MainService.ACTION_SEND_SCHEDULE);
        intent.putExtra(MainService.EXTRA_SCHEDULE_IDS, scheduleIds);
        intent.putExtra(MainService.EXTRA_SEND_NOW, true);

        // Launch MainService.
        startService(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EDIT_SCHEDULE && resultCode == RESULT_OK && data != null) {
            // Get schedule id.
            long rowId = data.getLongExtra(EditScheduleActivity.EXTRA_SCHEDULE_ID, -1);
            long status = data.getLongExtra(EditScheduleActivity.EXTRA_SCHEDULE_STATUS, 1);
            boolean isSendDirectly = data.getBooleanExtra(EditScheduleActivity.EXTRA_SEND_DIRECTLY,
                    false);
            boolean updateAlarm = false;
            ContentValues values = new ContentValues();

            // Get schedule name and status.
            values.put(ScheduleEntry.COLUMN_NAME,
                    data.getCharSequenceExtra(EditScheduleActivity.EXTRA_SCHEDULE_NAME)
                            .toString());
            if (isSendDirectly) {
                status = 0;
            }
            values.put(ScheduleEntry.COLUMN_STATUS, status);

            // Get repeat type and weekly repeat type.
            values.put(ScheduleEntry.COLUMN_REPEAT,
                    (long) data.getIntExtra(EditScheduleActivity.EXTRA_REPEAT_TYPE, 0));

            // Get repeat date and time.
            values.put(ScheduleEntry.COLUMN_DATETIME,
                    data.getStringExtra(EditScheduleActivity.EXTRA_REPEAT_DATETIME));

            // Get bulk file ID.
            String phoneDataIdStr = "";
            Long bulkFileId = data.getLongExtra(EditScheduleActivity.EXTRA_BULK_FILE_ID, -1);
            if (bulkFileId >= 0) {
                phoneDataIdStr += bulkFileId.toString() + ",";
            }

            // Get phone data IDs.
            @SuppressWarnings("unchecked")
            HashSet<Long> phoneDataIds = (HashSet<Long>) data
                    .getSerializableExtra(EditScheduleActivity.EXTRA_CONTACT_DATA_IDS);
            for (Long id : phoneDataIds) {
                phoneDataIdStr += id.toString() + ",";
            }
            values.put(ScheduleEntry.COLUMN_PHONE_DATA_IDS, phoneDataIdStr);

            // Get SMS content.
            values.put(ScheduleEntry.COLUMN_SMS_CONTENT,
                    data.getCharSequenceExtra(EditScheduleActivity.EXTRA_SMS_CONTENT)
                            .toString());

            if (status == 1) {
                // An activated schedule is added or modified.
                updateAlarm = true;
            } else if (getScheduleStatus(rowId) != status) {
                // A schedule is deactivated.
                updateAlarm = true;
            }

            // Update the schedule to database.
            rowId = setScheduleToDatabase(rowId, values);
            if (rowId >= 0) {
                // Update UI status.
                SimpleCursorAdapter adapter = (SimpleCursorAdapter) mListView.getAdapter();
                if (adapter != null) {
                    adapter.getCursor().requery();
                    adapter.notifyDataSetChanged();
                } else {
                    mListView.setAdapter(createSimpleCursorAdapter());
                }
            }

            // Update next schedule in AlarmManager.
            if (updateAlarm) {
                updateAlarmManager();
            }

            // Send the schedule directly.
            if (isSendDirectly) {
                // Launch MainService.
                startMainService(rowId);
            }
        } else if (requestCode == EDIT_SCHEDULE && resultCode == RESULT_CANCELED && data != null) {
            Long bulkFileId = data.getLongExtra(EditScheduleActivity.EXTRA_BULK_FILE_ID, -1);
            if (bulkFileId >= 0) {
                mDbExternalHelper.deleteBulkFile(bulkFileId);
            }

        }
    }

    private SimpleCursorAdapter createSimpleCursorAdapter() {
        // Build adapter with template entries
        Cursor cursor = mDbHelper.getScheduleCursor(null, null);
        if ((cursor != null) && (cursor.getCount() > 0)) {
            String[] fields = new String[] {
                    ScheduleEntry.COLUMN_NAME,
                    ScheduleEntry.COLUMN_STATUS,
                    ScheduleEntry.COLUMN_DATETIME,
                    ScheduleEntry.COLUMN_REPEAT,
            };
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    R.layout.schedule_entry, cursor, fields,
                    new int[] {
                            R.id.scheduleEntryName,
                            R.id.scheduleEntryStatus,
                            R.id.scheduleEntryTime,
                            R.id.scheduleEntryRepeat,
                    });

            adapter.setViewBinder(this);
            return adapter;
        }

        return null;
    }

    private void modifyScheduleStatus(long id, int status) {
        // Get row IDs of selected schedules.
        ArrayList<Long> rowIds = new ArrayList<Long>(1);
        if (id >= 0) {
            rowIds.add(id);
        } else {
            for (int i = 0; i < mListView.getAdapter().getCount(); i++) {
                rowIds.add(mListView.getAdapter().getItemId(i));
            }
        }

        // Get schedule status.
        ContentValues values = new ContentValues();
        values.put(ScheduleEntry.COLUMN_STATUS, status);

        // Update the schedule to database.
        for (Long rowId : rowIds) {
            setScheduleToDatabase(rowId, values);
        }

        // Update next schedule in AlarmManager.
        updateAlarmManager();

        // Update UI status.
        SimpleCursorAdapter adapter = (SimpleCursorAdapter) mListView.getAdapter();
        if (adapter != null) {
            adapter.getCursor().requery();
            adapter.notifyDataSetChanged();
        }
    }

    private long getScheduleStatus(long id) {
        if (id < 0) {
            return -1;
        }

        // Get the schedule with id.
        String selection = ScheduleEntry._ID + " = ?";
        String[] selectionArgs = {
                String.valueOf(id),
        };

        long status = -1;
        Cursor cursor = mDbHelper.getScheduleCursor(selection, selectionArgs);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                status = cursor.getLong(cursor.getColumnIndex(ScheduleEntry.COLUMN_STATUS));
            }
            cursor.close();
        }

        return status;
    }

    private long getScheduleBulkFile(long id) {
        if (id < 0) {
            return -1;
        }

        // Get the schedule with id.
        String selection = ScheduleEntry._ID + " = ?";
        String[] selectionArgs = {
                String.valueOf(id),
        };

        long bulkFile = -1;
        Cursor cursor = mDbHelper.getScheduleCursor(selection, selectionArgs);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                if (ScheduleRepeat.BULK == cursor.getInt(cursor
                        .getColumnIndex(ScheduleEntry.COLUMN_REPEAT))) {
                    String contactIdStr = cursor.getString(cursor
                            .getColumnIndex(ScheduleEntry.COLUMN_PHONE_DATA_IDS));
                    String[] contactIdStrs = contactIdStr.split(",");
                    bulkFile = Long.parseLong(contactIdStrs[0]);
                }
            }
            cursor.close();
        }

        return bulkFile;
    }

    private long setScheduleToDatabase(long id, ContentValues values) {
        long rowId = mDbHelper.updateSchedule(id, values);
        if (id < 0) {
            if (rowId < 0) {
                Log.e(TAG, "Insert new schedule error!");
            }
            return rowId;
        } else {
            if (rowId < 0) {
                Log.e(TAG, "Update existed schedule error!");
                return -1;
            }
            return id;
        }
    }

    private void deleteSchedule(long id) {
        Log.d(TAG, "Delete the schedule id: " + id);

        boolean updateAlarm = false;

        // Get the used bulk file.
        long bulkFileId = getScheduleBulkFile(id);

        // Delete the selected schedule.
        if (id < 0) {
            // Delete all schedules.
            updateAlarm = true;
        } else if (getScheduleStatus(id) == 1) {
            // Delete the activate schedule.
            updateAlarm = true;
        }
        mDbHelper.deleteSchdule(id);

        // Delete the non-used bulk files.
        if (id < 0) {
            // Delete all bulk files.
            mDbExternalHelper.deleteBulkFile(-1);
        } else if (bulkFileId >= 0) {
            // Delete the non-used bulk file.
            // One bulk file is only be used is one schedule.
            mDbExternalHelper.deleteBulkFile(bulkFileId);
        }

        // Update next schedule in AlarmManager.
        if (updateAlarm) {
            updateAlarmManager();
        }

        // Update UI status.
        SimpleCursorAdapter adapter = (SimpleCursorAdapter) mListView.getAdapter();
        if (adapter != null) {
            adapter.getCursor().requery();
            adapter.notifyDataSetChanged();
        } else {
            mListView.setAdapter(createSimpleCursorAdapter());
        }
    }

    @Override
    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        String columnName = cursor.getColumnName(columnIndex);
        if (columnName.equals(ScheduleEntry.COLUMN_STATUS)) {
            ImageView image = (ImageView) view;
            int status = cursor.getInt(columnIndex);
            if (status == 1) {
                // Schedule is running.
                image.setImageResource(R.drawable.ic_schedule_running);
            } else {
                // Schedule is paused.
                image.setImageResource(R.drawable.ic_schedule_paused);
            }
        } else if (columnName.equals(ScheduleEntry.COLUMN_DATETIME)) {
            // Get date and time.
            String dateTime = cursor.getString(columnIndex);

            // Update UI.
            TextView text = (TextView) view;
            text.setText("");
            try {
                Date date = mScheduleDateFormat.parse(dateTime);
                text.setText(mDatetimeDateFormat.format(date));
            } catch (ParseException e) {
                // Display an empty string.
            }
        } else if (columnName.equals(ScheduleEntry.COLUMN_REPEAT)) {
            // Get repeat
            int repeat = cursor.getInt(columnIndex);

            // Update UI.
            String dateTime = "";
            TextView text = (TextView) view;
            text.setText("");
            switch (repeat) {
                case EditScheduleActivity.SCHEDULE_REPEAT_TYPE_DAILY:
                case EditScheduleActivity.SCHEDULE_REPEAT_TYPE_MONTHLY:
                case EditScheduleActivity.SCHEDULE_REPEAT_TYPE_YEARLY:
                    text.setText(mRepeatTypes[repeat]);
                    break;
                case EditScheduleActivity.SCHEDULE_REPEAT_TYPE_NONE:
                    // Get date and time.
                    dateTime = cursor.getString(cursor
                            .getColumnIndex(ScheduleEntry.COLUMN_DATETIME));
                    Calendar now = Calendar.getInstance();
                    try {
                        Date date = mScheduleDateFormat.parse(dateTime);
                        // Check out-of-date.
                        if (date.before(now.getTime())) {
                            text.setText(mRepeatTypes[repeat]);
                        }
                    } catch (ParseException e) {
                        // Display an empty string.
                    }
                    break;
                case EditScheduleActivity.SCHEDULE_REPEAT_TYPE_WEEKLY:
                    // Get date and time.
                    dateTime = cursor.getString(cursor
                            .getColumnIndex(ScheduleEntry.COLUMN_DATETIME));
                    try {
                        Date date = mScheduleDateFormat.parse(dateTime);
                        // Display day of week.
                        text.setText(mWeekDateFormat.format(date));
                    } catch (ParseException e) {
                        // Ignore the exception.
                    }
                    break;
                case EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BIRTHDAY:
                    setRepeatBirthdayValue((TextView) view, cursor);
                    break;
                case EditScheduleActivity.SCHEDULE_REPEAT_TYPE_BULK:
                    text.setText(R.string.csv_import);
                    break;
                default:
                    break;
            }
        } else {
            return false;
        }

        return true;
    }

    private void setRepeatBirthdayValue(TextView text, Cursor cursor) {
        text.setText(getString(R.string.schedule_repeat_birthday));

        // Get schedule's date.
        String scheduleTime = cursor.getString(cursor
                .getColumnIndex(ScheduleEntry.COLUMN_DATETIME));
        try {
            Date date = mScheduleDateFormat.parse(scheduleTime);
            scheduleTime = mBirthdayFormat.format(date);
        } catch (ParseException e) {
            // Ignore the exception.
            return;
        }

        // Get schedule's phone data IDs.
        String contactDataIdStr = cursor.getString(cursor
                .getColumnIndex(ScheduleEntry.COLUMN_PHONE_DATA_IDS));

        // Get contacts with the same birthday.
        HashSet<Long> contactIds = new HashSet<Long>();
        Cursor eventCursor = ContactUtility.getEventCursor(getContentResolver(), null);
        if (eventCursor != null) {
            if (eventCursor.moveToFirst()) {
                do {
                    String startDate = eventCursor.getString(eventCursor
                            .getColumnIndex(Event.START_DATE));
                    if (startDate.indexOf(scheduleTime) >= 0) {
                        contactIds.add(eventCursor.getLong(eventCursor
                                .getColumnIndex(Event.CONTACT_ID)));
                        break;
                    }
                } while (eventCursor.moveToNext());
            }
            eventCursor.close();
        }
        if (contactIds.size() == 0)
            return;

        // Compare contact's data IDs to find out the name.
        Cursor phoneCursor = ContactUtility.getPhoneCursorByContact(getContentResolver(),
                contactIds);
        if (phoneCursor != null) {
            if (phoneCursor.moveToFirst()) {
                do {
                    long dataId = phoneCursor.getLong(phoneCursor
                            .getColumnIndex(ContactsContract.Data._ID));
                    if (contactDataIdStr.indexOf(String.valueOf(dataId)) >= 0) {
                        text.setText(phoneCursor.getString(phoneCursor
                                .getColumnIndex(Contacts.DISPLAY_NAME)));
                        break;
                    }
                } while (phoneCursor.moveToNext());
            }
            phoneCursor.close();
        }
    }

    private void updateAlarmManager() {
        Log.d(TAG, "updateAlarmManager");

        // Launch MainService to add the schedule to AlarmManager.
        Intent serviceIntent = new Intent(this, MainService.class);
        serviceIntent.setAction(MainService.ACTION_ADD_TO_ALARM);
        startService(serviceIntent);
    }

    private void updateLimit(Boolean isPremium) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        // Store schedule limit into SharedPreferences.
        if (isPremium != null) {
            SharedPreferences.Editor spe = sp.edit();
            if (isPremium) {
                // Extend the schedule and contact limit.
                spe.putInt("schedule_limit", 100);
                spe.putInt("contact_limit", 500);
            } else {
                spe.putInt("schedule_limit", 1);
                spe.putInt("contact_limit", 20);
            }
            spe.commit();
        }

        // Update schedule limit.
        if (MainUtility.isDebug(getApplicationInfo())) {
            mScheduleLimit = 100;
        } else {
            mScheduleLimit = sp.getInt("schedule_limit", 1);
        }
    }
}
