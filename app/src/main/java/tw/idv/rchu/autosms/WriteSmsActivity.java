
package tw.idv.rchu.autosms;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;

class ContactSpinnerItem {
    private long mDataId;
    private String mContactId;
    private String mName;
    private String mPhone;

    ContactSpinnerItem(long dataId, String contactId, String name, String phone) {
        this.mDataId = dataId;
        this.mContactId = contactId;
        this.mName = name;
        this.mPhone = phone;
    }

    long getDataId() {
        return mDataId;
    }

    String getContactId() {
        return mContactId;
    }

    @Override
    public String toString() {
        return mName + " (" + mPhone + ")";
    }
}

public class WriteSmsActivity extends ActionBarActivity implements DialogInterface.OnClickListener {
    static final String TAG = "WriteSmsActivity";
    static final String EXTRA_SELECTED_CONTACT_DATA_IDS = "contact_data_ids";
    static final String EXTRA_SMS_CONTENT = "sms_content";
    static final String EXTRA_BULK_FILE_ID = "bulk_file_id";

    private static final int PICK_CONTACT = 1;

    private Context mContext;
    private DBExternalHelper mDbExternalHelper;
    private SmartElementController mSmartElement;
    private HashSet<Long> mContactDataIds = new HashSet<Long>();
    private boolean mIsBirthdayMode;
    private long mBulkFileId;
    private int mContactItemPosition = -1;
    private ContactSpinnerItem[] mContactItems;
    private String[] mTemplates;
    private AlertDialog mAlertDialog;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_sms);

        mContext = this;
        
        // TODO: use AsyncTask to open database.
        mDbExternalHelper = new DBExternalHelper(this);
        mDbExternalHelper.openWritable();        
        
        mSmartElement = new SmartElementController(getContentResolver(), mDbExternalHelper, getResources());
        mTemplates = getTemplates();

        // Setup action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getIntent().hasExtra(EXTRA_SELECTED_CONTACT_DATA_IDS)) {
            // Get selected contacts.
            mContactDataIds = (HashSet<Long>) getIntent().getSerializableExtra(
                    EXTRA_SELECTED_CONTACT_DATA_IDS);
        }
        mIsBirthdayMode = getIntent()
                .getBooleanExtra(AddContactActivity.EXTRA_BIRTHDAY_MODE, false);

        generateContactComboBox();
        
        mBulkFileId = getIntent().getLongExtra(EXTRA_BULK_FILE_ID, -1);

        EditText editView = (EditText) findViewById(R.id.edit_text_content);
        if (getIntent().hasExtra(EXTRA_SMS_CONTENT)) {
            // Get the SMS content.
            editView.setText(getIntent().getCharSequenceExtra(EXTRA_SMS_CONTENT));
        }
        editView.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                int[] len = SmsMessage.calculateLength(s, false);
                getSupportActionBar().setTitle(
                        String.format("%s (%d/%d)",
                                getString(R.string.short_message), len[2], len[0]));
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (MainUtility.hasIceCreamSandwich()) {
            getMenuInflater().inflate(R.menu.activity_write_sms, menu);

            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getItemId() == R.id.itemAddContact) {
                    MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
                } else {
                    MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
                }
            }            
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (getIntent().getAction().equals(Intent.ACTION_EDIT)) {
            menu.findItem(R.id.itemSend).setVisible(false);
        } else {
            menu.findItem(R.id.itemSend).setVisible(true);
        }

        if (mBulkFileId >= 0) {
            menu.findItem(R.id.itemAddContact).setVisible(false);
        } else {
            menu.findItem(R.id.itemInsertColumn).setVisible(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.itemAddContact:
                onAddContactButtonClicked(null);
                return true;
            case R.id.itemInsertTag:
                onInsertTagButtonClicked(null);
                return true;
            case R.id.itemInsertColumn:
                onInsertColumnButtonClicked(null);
                return true;
            case R.id.itemInsertTemplate:
                onInsertTemplateButtonClicked(null);
                return true;
            case R.id.itemPreview:
                onPreviewButtonClicked(null);
                return true;
            case R.id.itemSend:
                onSendButtonClicked(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();

        // Put SMS content.
        EditText editView = (EditText) findViewById(R.id.edit_text_content);
        if (editView.getText().length() > 0) {
            intent.putExtra(EXTRA_SMS_CONTENT, editView.getText());
        }

        // Put contact data IDs.
        if (mContactDataIds.size() > 0) {
            intent.putExtra(EXTRA_SELECTED_CONTACT_DATA_IDS, mContactDataIds);
        }

        // Back to MainActivity or EditScheduleActivity.
        setResult(RESULT_OK, intent);
        finish();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_CONTACT) {
            if ((resultCode == RESULT_OK) && (data != null)) {
                mContactDataIds = (HashSet<Long>) data
                        .getSerializableExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS);
                generateContactComboBox();
            }
        }
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
        mDbExternalHelper.closeWritable();        

        super.onDestroy();
    }

    public void onContactComboBoxClicked(View view) {
        // No contacts can be showed up.
        if (mContactItems == null || mContactItems.length == 0) {
            return;
        }

        // Create contact list data.
        String[] items = new String[mContactItems.length];
        final boolean[] checkedItems = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = mContactItems[i].toString();
            checkedItems[i] = false;
        }

        // Create contact list alert dialog and show it.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        mAlertDialog = builder.setTitle(getString(R.string.delete_contacts))
                .setMultiChoiceItems(items, checkedItems,
                        new DialogInterface.OnMultiChoiceClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                checkedItems[which] = isChecked;
                            }
                        })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Remove contacts.
                        for (int i = checkedItems.length - 1; i >= 0; i--) {
                            if (checkedItems[i]) {
                                mContactDataIds.remove(mContactItems[i].getDataId());
                            }
                        }

                        generateContactComboBox();
                    }
                }).setNegativeButton(android.R.string.cancel, null).create();
        mAlertDialog.show();
    }
    
    public void onAddContactButtonClicked(View view) {
        Intent intent = new Intent(this, AddContactActivity.class);
        intent.setAction(Intent.ACTION_PICK);
        intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
        if (mContactDataIds.size() > 0) {
            intent.putExtra(EXTRA_SELECTED_CONTACT_DATA_IDS, mContactDataIds);
        }
        intent.putExtra(AddContactActivity.EXTRA_BIRTHDAY_MODE, mIsBirthdayMode);

        startActivityForResult(intent, PICK_CONTACT);        
    }
    
    public void onInsertTagButtonClicked(View view) {
        // Show "Insert Tag" dialog.
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        mAlertDialog = builder.setTitle(getString(R.string.insert_tag))
                .setItems(mSmartElement.getTags(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Insert the tag.
                        EditText editView = (EditText) findViewById(R.id.edit_text_content);
                        int pos = editView.getSelectionStart();
                        editView.getText().insert(pos,
                                "[[" + mSmartElement.getTagValue(which) + "]]");

                        // Warn user the SMS calculation may wrong.
                        Toast.makeText(mContext, getString(R.string.msg_number_wrong_insert_tag),
                                Toast.LENGTH_SHORT).show();

                        // Close dialog.
                        dialog.dismiss();
                    }
                }).create();
        mAlertDialog.show();
    }

    public void onInsertColumnButtonClicked(View view) {
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

                        // Warn user the SMS calculation may wrong.
                        Toast.makeText(mContext, getString(R.string.msg_number_wrong_insert_tag),
                                Toast.LENGTH_SHORT).show();

                        // Close dialog.
                        dialog.dismiss();
                    }
                }).create();
        mAlertDialog.show();
    }
    
    public void onInsertTemplateButtonClicked(View view) {    
        if (mTemplates == null) {
            return;
        }

        // Show "Insert Template" dialog.
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        mAlertDialog = builder.setTitle(getString(R.string.insert_template))
                .setItems(mTemplates, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Insert the template.
                        EditText editView = (EditText) findViewById(R.id.edit_text_content);
                        int pos = editView.getSelectionStart();
                        editView.getText().insert(pos, mTemplates[which]);

                        // Close dialog.
                        dialog.dismiss();
                    }
                }).create();
        mAlertDialog.show();
    }

    public void onPreviewButtonClicked(View view) {
        if (mContactDataIds.size() == 0) {
            return;
        }
        EditText editView = (EditText) findViewById(R.id.edit_text_content);
        if (editView.getText().length() == 0) {
            return;
        }

        // Create SMS preview dialog.
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        mAlertDialog = new AlertDialog.Builder(this).setTitle(R.string.preview).create();
        final View viewDialog = mAlertDialog.getLayoutInflater().inflate(R.layout.dialog_sms_preview,
                null);

        // Set listener of "Previous" button.
        ImageButton buttonPrevious = (ImageButton) viewDialog.findViewById(R.id.imageButtonPrevious);
        ImageButton buttonNext = (ImageButton) viewDialog.findViewById(R.id.imageButtonNext);
        if (mContactDataIds.size() > 1) {
            buttonPrevious.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // Show previous SMS content preview.
                    if (mContactItemPosition > 0) {
                        mContactItemPosition--;
                    } else {
                        mContactItemPosition = mContactItems.length - 1;
                    }
                    TextView textView = (TextView) findViewById(R.id.textViewContact);
                    textView.setText(mContactItems[mContactItemPosition].toString());

                    textView = (TextView) viewDialog.findViewById(R.id.textViewMessage);
                    textView.setText(generatePreviewMessage(
                            mContactItems[mContactItemPosition].getContactId(),
                            mBulkFileId,
                            mContactItems[mContactItemPosition].getDataId()));
                }
            });

            buttonNext.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // Show next SMS content preview.
                    if (mContactItemPosition < mContactItems.length - 1) {
                        mContactItemPosition++;
                    } else {
                        mContactItemPosition = 0;
                    }
                    TextView textView = (TextView) findViewById(R.id.textViewContact);
                    textView.setText(mContactItems[mContactItemPosition].toString());

                    textView = (TextView) viewDialog.findViewById(R.id.textViewMessage);
                    textView.setText(generatePreviewMessage(
                            mContactItems[mContactItemPosition].getContactId(),
                            mBulkFileId,
                            mContactItems[mContactItemPosition].getDataId()));
                }
            });
        } else {
            buttonPrevious.setEnabled(false);
            buttonNext.setEnabled(false);
        }

        // Show first SMS content preview.
        TextView text = (TextView) viewDialog.findViewById(R.id.textViewMessage);
        text.setText(generatePreviewMessage(
                mContactItems[mContactItemPosition].getContactId(),
                mBulkFileId,
                mContactItems[mContactItemPosition].getDataId()));

        mAlertDialog.setView(viewDialog);
        mAlertDialog.show();
    }

    public void onSendButtonClicked(View view) {
        // Check SMS address and body.
        if (mContactDataIds.size() == 0) {
            Toast.makeText(this, getString(R.string.msg_choose_contact), Toast.LENGTH_SHORT).show();
            return;
        }
        EditText editView = (EditText) findViewById(R.id.edit_text_content);
        if (editView.getText().length() == 0) {
            Toast.makeText(this, getString(R.string.msg_input_content), Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sharedPreference = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreference.getBoolean(SettingFragment.KEY_CONFIRM_SEND, false)) {
            // Show a confirm dialog.
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            mAlertDialog = builder.setTitle("").setMessage(getString(R.string.msg_send_sms))
                    .setPositiveButton(android.R.string.ok, this)
                    .setNegativeButton(android.R.string.cancel, this).create();
            mAlertDialog.show();
        } else {
            // Send SMS directly.
            onClick(null, DialogInterface.BUTTON_POSITIVE);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // Create SMS sending events by contact data IDs.
            EditText editView = (EditText) findViewById(R.id.edit_text_content);
            ArrayList<Intent> smsIntents = ContactUtility.createSmsIntent(getContentResolver(),
                    mSmartElement, mContactDataIds, editView.getText().toString(), -1);
            if (smsIntents != null && smsIntents.size() > 0) {
                String datetime = DBInternalHelper.getScheduleDateFormat().format(
                        Calendar.getInstance().getTime());

                // Start SendSmsService to send SMS.
                Intent intent = new Intent(this, SendSmsService.class);
                intent.setAction(SendSmsService.ACTION_SEND);
                intent.putExtra(SendSmsService.EXTRA_SMS_INTENT, smsIntents);
                intent.putExtra(SendSmsService.EXTRA_SMS_NOTIFICATION, false);
                intent.putExtra(SendSmsService.EXTRA_HISTORY_NAME, getString(R.string.send_now));
                intent.putExtra(SendSmsService.EXTRA_HISTORY_DATETIME, datetime);

                startService(intent);
            }

            // Back to MainActivity.
            setResult(RESULT_CANCELED, null);
            finish();
        }
    }

    private String[] getTemplates() {
        DBExternalHelper dbHelper = new DBExternalHelper(this);
        // TODO: use AsyncTask to open database.
        dbHelper.openWritable();

        // Get templates from database.
        String[] templates = null;
        Cursor cursor = dbHelper.getTemplateCursor();
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                templates = new String[cursor.getCount()];
                for (int i = 0; i < cursor.getCount(); i++) {
                    if (cursor.moveToPosition(i)) {
                        templates[i] = cursor.getString(cursor
                                .getColumnIndex(DBExternalHelper.TemplateEntry.COLUMN_CONTENT));
                    } else {
                        templates[i] = "";
                    }
                }
            }
            cursor.close();
        }

        // Close database.
        dbHelper.closeWritable();

        return templates;
    }

    private String generatePreviewMessage(String contactId, long bulkFileId, long phoneDataId) {
        // Get SMS content.
        EditText editView = (EditText) findViewById(R.id.edit_text_content);

        // Replace smart elements from the selected contact.
        String content = mSmartElement.replaceAllElements(contactId, editView.getText().toString());
        if (mBulkFileId >= 0) {
            content = mSmartElement.replaceAllColumns(String.valueOf(bulkFileId), String.valueOf(phoneDataId), content);
        }
        return content;
    }

    private void generateContactComboBox() {
        if (mContactDataIds.size() > 0) {
            mContactItems = new ContactSpinnerItem[mContactDataIds.size()];
            int itemCount = 0;
            Cursor cursor = ContactUtility.getPhoneCursor(getContentResolver(), mContactDataIds);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        mContactItems[itemCount] = new ContactSpinnerItem(
                                cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID)),
                                cursor.getString(cursor.getColumnIndex(Phone.CONTACT_ID)),
                                cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME)),
                                cursor.getString(cursor.getColumnIndex(Phone.NUMBER)));
                        itemCount++;
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }

            // Show first contact's name.
            mContactItemPosition = 0;
            TextView textView = (TextView) findViewById(R.id.textViewContact);
            textView.setText(mContactItems[0].toString());

            View view = findViewById(R.id.layoutContactComboBox);
            view.setEnabled(true);
        } else {
            mContactItems = null;

            // Show nothing.
            mContactItemPosition = -1;
            TextView textView = (TextView) findViewById(R.id.textViewContact);
            textView.setText(R.string.no_contact);

            View view = findViewById(R.id.layoutContactComboBox);
            view.setEnabled(false);
        }
    }
}
