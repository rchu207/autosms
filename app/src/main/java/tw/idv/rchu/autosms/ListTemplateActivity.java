
package tw.idv.rchu.autosms;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import tw.idv.rchu.autosms.DBExternalHelper.TemplateEntry;

public class ListTemplateActivity extends ActionBarActivity {
    static final String TAG = "ListTemplateActivity";
    private static final int EDIT_TEMPLATE = 1;

    private ListView mListView;
    private AlertDialog mAlertDialog;

    private DBExternalHelper mDbHelper;

    private AdapterView.OnItemClickListener mListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Launch EditTemplateActivity.
            startEditTemplateActivity(position, id);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_schedule);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(mListener);

        // Initialize database.
        // TODO: use AsyncTask to open database.
        mDbHelper = new DBExternalHelper(this);
        mDbHelper.openWritable();

        // Build adapter with templates.
        mListView.setAdapter(createSimpleCursorAdapter());

        // Register context menu.
        registerForContextMenu(mListView);

        // Setup action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_list_template, menu);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() == R.id.itemAddTemplate) {
                MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
            }
        }

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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.activity_list_template_context, menu);
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
        mDbHelper.closeWritable();

        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.itemAddTemplate:
                // Start EditTemplateActivity to add a new template.
                Intent intent = new Intent(this, EditTemplateActivity.class);
                intent.setAction(Intent.ACTION_EDIT);
                startActivityForResult(intent, EDIT_TEMPLATE);
                break;
            case R.id.itemDeleteAll:
                // Show confirm dialog to delete all templates.
                if (mAlertDialog != null) {
                    mAlertDialog.dismiss();
                }
                mAlertDialog = new AlertDialog.Builder(this)
                        .setTitle("")
                        .setMessage(getString(R.string.msg_delete_all_templates))
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        deleteTemplate(-1);
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

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.itemEdit:
                // Launch EditTemplateActivity.
                startEditTemplateActivity(info.position, info.id);
                break;
            case R.id.itemDelete:
                deleteTemplate(info.id);
                break;
            default:
                return super.onContextItemSelected(item);
        }

        return true;
    }

    private void startEditTemplateActivity(int position, long id) {
        // Prepare intent to edit the template.
        Intent intent = new Intent(this, EditTemplateActivity.class);
        intent.setAction(Intent.ACTION_EDIT);
        intent.putExtra(EditTemplateActivity.EXTRA_TEMPLATE_ID, id);
        Cursor cursor = (Cursor) mListView.getItemAtPosition(position);
        String content = cursor.getString(cursor.getColumnIndex(TemplateEntry.COLUMN_CONTENT));
        intent.putExtra(EditTemplateActivity.EXTRA_TEMPLATE, content);

        // Launch EditTemplateActivity.
        startActivityForResult(intent, EDIT_TEMPLATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EDIT_TEMPLATE) {
            if ((resultCode == RESULT_OK) && (data != null)) {
                long rowId = data.getLongExtra(EditTemplateActivity.EXTRA_TEMPLATE_ID, -1);
                String template = data.getStringExtra(EditTemplateActivity.EXTRA_TEMPLATE);
                if (template.length() > 0) {
                    if (updateTemplate(rowId, template)) {
                        // Update UI status.
                        SimpleCursorAdapter adapter = (SimpleCursorAdapter) mListView.getAdapter();
                        if (adapter != null) {
                            adapter.getCursor().requery();
                            adapter.notifyDataSetChanged();
                        } else {
                            mListView.setAdapter(createSimpleCursorAdapter());
                        }
                    }
                }
            }
        }
    }

    private SimpleCursorAdapter createSimpleCursorAdapter() {
        Cursor cursor = mDbHelper.getTemplateCursor();
        if ((cursor != null) && (cursor.getCount() > 0)) {
            String[] fields = new String[] {
                    TemplateEntry.COLUMN_CONTENT
            };
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    android.R.layout.simple_list_item_1, cursor,
                    fields, new int[] {
                            android.R.id.text1
                    });
            return adapter;
        }

        return null;
    }

    private boolean updateTemplate(long id, String template) {
        ContentValues values = new ContentValues();
        values.put(TemplateEntry.COLUMN_CONTENT, template);

        long rowId = mDbHelper.updateTemplate(id, values);
        if (rowId >= 0) {
            return true;
        } else {
            if (id < 0) {
                Log.e(TAG, "Insert new template error!");
            } else {
                Log.e(TAG, "Update existed template error!");
            }
            return false;
        }
    }

    private void deleteTemplate(long id) {
        mDbHelper.deleteTemplate(id);

        // Update UI status.
        SimpleCursorAdapter adapter = (SimpleCursorAdapter) mListView.getAdapter();
        if (adapter != null) {
            adapter.getCursor().requery();
            adapter.notifyDataSetChanged();
        } else {
            mListView.setAdapter(createSimpleCursorAdapter());
        }
    }
}
