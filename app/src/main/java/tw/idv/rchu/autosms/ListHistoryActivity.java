
package tw.idv.rchu.autosms;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;

import tw.idv.rchu.autosms.DBExternalHelper.HistoryEntry;
import tw.idv.rchu.autosms.DBExternalHelper.HistoryResult;
import tw.idv.rchu.autosms.DBExternalHelper.HistoryResultEntry;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ListHistoryActivity extends ActionBarActivity {
    static final String TAG = "ListHistoryActivity";

    private Context mContext;
    private Handler mUiHandler;
    private ListView mListView;
    private AlertDialog mAlertDialog;
    
    private DBExternalHelper mDbHelper;
    private SimpleDateFormat mScheduleDateFormat;
    private DateFormat mDatetimeDateFormat;
    private String[] mResultErrors;

    /**
     * Receiver for messages sent from MainService .
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "History is updated by MainService.");

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
            Cursor cursor = mDbHelper.getHistoryResultCursor(id);
            if (cursor == null) {
                return;
            }

            String[] items = null;
            if (cursor.getCount() > 0) {
                items = new String[cursor.getCount()];
                for (int i = 0; i < cursor.getCount(); i++) {
                    if (cursor.moveToPosition(i)) {
                        StringBuffer sb = new StringBuffer(32);
                        int result = cursor.getInt(cursor
                                .getColumnIndex(HistoryResultEntry.COLUMN_RESULT));
                        if (result == HistoryResult.SUCCESS) {
                            sb.append("O - ");
                        } else {
                            sb.append("X - ");
                        }
                        sb.append(cursor.getString(cursor
                                .getColumnIndex(HistoryResultEntry.COLUMN_CONTACT)));
                        sb.append(" (");
                        sb.append(cursor.getString(cursor
                                .getColumnIndex(HistoryResultEntry.COLUMN_ADDRESS)));
                        sb.append(")");

                        items[i] = sb.toString();
                    } else {
                        items[i] = "";
                    }
                }
            }
            cursor.close();

            // Show list dialog to display the history results.
            TextView textView = (TextView) view.findViewById(android.R.id.text1);
            if ((items != null) && (textView != null)) {
                if (mAlertDialog != null) {
                    mAlertDialog.dismiss();
                }   
                mAlertDialog = new AlertDialog.Builder(mContext)
                        .setTitle(textView.getText())
                        .setItems(items, null)
                        .setNeutralButton(android.R.string.ok, null).create();
                mAlertDialog.show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_schedule);

        mContext = this;
        mUiHandler = new Handler();

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(mListener);

        // Initialize database.
        // TODO: use AsyncTask to open database.
        mDbHelper = new DBExternalHelper(this);
        mDbHelper.openWritable();
        mScheduleDateFormat = DBInternalHelper.getScheduleDateFormat();
        mDatetimeDateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        mResultErrors = getResources().getStringArray(R.array.msg_history_result_errors);

        // Build adapter with templates.
        SimpleCursorAdapter adapter = createSimpleCursorAdapter();
        mListView.setAdapter(adapter);

        // TODO: Register context menu.
        registerForContextMenu(mListView);

        // Setup action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Message handling - from MainService.
        final IntentFilter myFilter = new
                IntentFilter(MainService.BROADCAST_ACTION_HISTORY_UPDATED);
        registerReceiver(mReceiver, myFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_list_history, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mListView.getCount() > 0) {
            menu.findItem(R.id.itemDeleteAll).setEnabled(true);
        } else {
            menu.findItem(R.id.itemDeleteAll).setEnabled(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    protected void onStop() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        super.onStop();
    }    

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);

        mDbHelper.closeWritable();

        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.itemDeleteAll:
                // Show confirm dialog to delete all histories.
                if (mAlertDialog != null) {
                    mAlertDialog.dismiss();
                }   
                mAlertDialog = new AlertDialog.Builder(this)
                        .setTitle("")
                        .setMessage(getString(R.string.msg_delete_all_history))
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mDbHelper.deleteHistory(-1);

                                        // Update UI status.
                                        SimpleCursorAdapter adapter = (SimpleCursorAdapter) mListView
                                                .getAdapter();
                                        if (adapter != null) {
                                            adapter.getCursor().requery();
                                            adapter.notifyDataSetChanged();
                                        } else {
                                            mListView.setAdapter(createSimpleCursorAdapter());
                                        }
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null).create();
                mAlertDialog.show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private SimpleCursorAdapter createSimpleCursorAdapter() {
        Cursor cursor = mDbHelper.getHistoryCursor();
        if (cursor == null) {
            return null;
        }

        // Create adapter.
        String[] fields = new String[] {
                HistoryEntry.COLUMN_NAME,
                HistoryEntry.COLUMN_DATETIME,
        };
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2, cursor,
                fields, new int[] {
                        android.R.id.text1,
                        android.R.id.text2,
                });

        // Setup view binder of adapter.
        adapter.setViewBinder(new ViewBinder() {

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                String columnName = cursor.getColumnName(columnIndex);
                StringBuffer sb = new StringBuffer(32);
                if (columnName.equals(HistoryEntry.COLUMN_NAME)) {
                    // Get name.
                    sb.append(cursor.getString(columnIndex));

                    // Get result.
                    int result = cursor.getInt(cursor.getColumnIndex(HistoryEntry.COLUMN_RESULT));
                    sb.append(" [");
                    if (result == HistoryResult.SUCCESS) {
                        sb.append(getString(R.string.msg_history_result_success));
                    } else if (result == HistoryResult.PENDING) {
                        sb.append(getString(R.string.msg_history_result_pending));
                    } else {
                        sb.append(getString(R.string.msg_history_result_failed));
                    }
                    sb.append("]");

                    // Update UI.
                    TextView text = (TextView) view;
                    text.setText(sb.toString());
                } else if (columnName.equals(HistoryEntry.COLUMN_DATETIME)) {
                    // Get result.
                    int result = cursor.getInt(cursor.getColumnIndex(HistoryEntry.COLUMN_RESULT));
                    if ((result != HistoryResult.SUCCESS) && (result != HistoryResult.PENDING)) {
                        result = Math.abs(result);
                        if (result < mResultErrors.length) {
                            sb.append(mResultErrors[result]);
                        } else {
                            sb.append(mResultErrors[0]);
                        }
                    }

                    // Get date and time.
                    String dateTime = cursor.getString(columnIndex);
                    try {
                        Date date = mScheduleDateFormat.parse(dateTime);
                        if (sb.length() > 0) {
                            sb.append(" - ");
                        }
                        sb.append(mDatetimeDateFormat.format(date));
                    } catch (ParseException e) {
                        // Display an empty string.
                    }

                    // Update UI.
                    TextView text = (TextView) view;
                    text.setText(sb.toString());
                } else {
                    return false;
                }

                return true;
            }
        });

        return adapter;
    }
}
