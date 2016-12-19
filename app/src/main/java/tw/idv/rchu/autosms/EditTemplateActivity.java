
package tw.idv.rchu.autosms;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

public class EditTemplateActivity extends ActionBarActivity {
    static final String TAG = "EditTemplateActivity";
    static final String EXTRA_TEMPLATE_ID = "template_id";
    static final String EXTRA_TEMPLATE = "template";

    private SmartElementController mSmartElement;
    private long mTemplateId;
    private String mContent;

    private AlertDialog mAlertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_template);

        mSmartElement = new SmartElementController(getContentResolver(), getResources());

        Intent intent = getIntent();
        mTemplateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1);
        if (intent.hasExtra(EXTRA_TEMPLATE)) {
            mContent = intent.getStringExtra(EXTRA_TEMPLATE);
            EditText editText = (EditText) findViewById(R.id.edit_text_content);
            editText.setText(mContent);
        } else {
            mContent = "";
        }

        // Setup action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_edit_template, menu);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.itemInsertTag:
                onInsertTagMenuClicked(item);
                break;
            case R.id.itemInsertCsvTag:
                onInsertColumnButtonClicked(item);
                break;
            case R.id.itemSave:
                onSaveMenuClicked(item);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    public void onInsertTagMenuClicked(MenuItem item) {
        // Show "Insert Tag" dialog.
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        mAlertDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.insert_tag))
                .setItems(mSmartElement.getTags(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Insert the tag.
                        EditText editView = (EditText) findViewById(R.id.edit_text_content);
                        int pos = editView.getSelectionStart();
                        editView.getText().insert(pos,
                                "[[" + mSmartElement.getTagValue(which) + "]]");

                        // Close dialog.
                        dialog.dismiss();
                    }
                }).create();
        mAlertDialog.show();
    }

    public void onInsertColumnButtonClicked(MenuItem item) {
        // Show "Insert Column Tag" dialog.
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        mAlertDialog = builder.setTitle(getString(R.string.insert_column_tag))
                .setItems(mSmartElement.getColumns(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Insert the tag.
                        EditText editView = (EditText) findViewById(R.id.edit_text_content);
                        int pos = editView.getSelectionStart();
                        editView.getText().insert(pos,
                                "[[" + mSmartElement.getColumnValue(which) + "]]");

                        // Close dialog.
                        dialog.dismiss();
                    }
                }).create();
        mAlertDialog.show();
    }

    public void onSaveMenuClicked(MenuItem item) {
        // Return the selected contacts.
        Intent intent = new Intent();

        EditText editView = (EditText) findViewById(R.id.edit_text_content);
        if (editView.getText().length() > 0) {
            intent.putExtra(EXTRA_TEMPLATE_ID, mTemplateId);
            intent.putExtra(EXTRA_TEMPLATE, editView.getText().toString());
            setResult(RESULT_OK, intent);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.msg_input_content), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // Get new template.
        EditText editView = (EditText) findViewById(R.id.edit_text_content);
        String newContent = editView.getText().toString();

        if (!mContent.equals(newContent)) {
            // Show exit confirm dialog.
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }

            if (newContent.length() > 0) {
                mAlertDialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.msg_template_is_changed_save))
                        .setPositiveButton(R.string.yes,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Save the schedule.
                                        onSaveMenuClicked(null);
                                    }
                                })
                        .setNegativeButton(R.string.no,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Finish the activity.
                                        finish();
                                    }
                                })
                        .create();
            } else {
                mAlertDialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.msg_template_is_changed_ignore))
                        .setPositiveButton(R.string.yes,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Finish the activity.
                                        finish();
                                    }
                                })
                        .setNegativeButton(R.string.no, null)
                        .create();
            }

            mAlertDialog.show();
        } else {
            // Finish the activity.
            finish();
        }
    }
}
