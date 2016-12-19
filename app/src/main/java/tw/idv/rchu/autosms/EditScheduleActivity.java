
package tw.idv.rchu.autosms;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


public class EditScheduleActivity extends ActionBarActivity {
    private static final String TAG = "[iSend]EditSchedule";

    private static final int PICK_CONTACT = 1;
    private static final int EDIT_SMS_CONTENT = 2;

    static final String EXTRA_SCHEDULE_ID = "schedule_id";
    static final String EXTRA_REPEAT_TYPE = "repeat_type";
    static final String EXTRA_SCHEDULE_NAME = "schedule_name";
    static final String EXTRA_SCHEDULE_STATUS = "schedule_status";
    static final String EXTRA_REPEAT_DATETIME = "repeat_date_time";
    static final String EXTRA_CONTACT_DATA_IDS = "contact_data_ids";
    static final String EXTRA_SMS_CONTENT = "sms_content";
    static final String EXTRA_BULK_FILE_ID = "bulk_file_id";
    static final String EXTRA_SEND_DIRECTLY = "send_directly";

    static final String REPEAT_WEEKLY = "repeat_weekly";
    static final String REPEAT_DATE = "repeat_date";
    static final String REPEAT_TIME = "repeat_time";
    static final String REPEAT_BIRTHDAY = "repeat_birthday";
    static final String REPEAT_BULK = "repeat_bulk";
    static final String REPEAT_SOS = "repeat_sos";

    static final int SCHEDULE_REPEAT_TYPE_NONE = 0;
    static final int SCHEDULE_REPEAT_TYPE_DAILY = 1;
    static final int SCHEDULE_REPEAT_TYPE_WEEKLY = 2;
    static final int SCHEDULE_REPEAT_TYPE_MONTHLY = 3;
    static final int SCHEDULE_REPEAT_TYPE_YEARLY = 4;
    static final int SCHEDULE_REPEAT_TYPE_BIRTHDAY = 100;
    static final int SCHEDULE_REPEAT_TYPE_BULK = 200;
    static final int SCHEDULE_REPEAT_TYPE_SOS = 300;
    
    static final int SCHEDULE_STATUS_OFF = 0;
    static final int SCHEDULE_STATUS_ON = 1;

    private Context mContext;
    private SimpleDateFormat mScheduleDateFormat;
    private long mScheduleId;
    private int mRepeatType;
    private String[] mRepeatTypes;
    private String[] mWeeklyTypes;
    private Calendar mDateTime;
    private Calendar mOriginalDateTime;
    private HashSet<Long> mContactDataIds;
    private CharSequence mSmsContent;
    private long mBulkFileId;

    private ListView mListView;
    private MyDatePickerDialog mDateDialog;
    private TimePickerDialog mTimeDialog;
    private AlertDialog mAlertDialog;
    private ContactSpinnerItem[] mContactItems;

    OnItemClickListener mItemClickedListener = new OnItemClickListener() {

        @SuppressWarnings("unchecked")
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Log.d(TAG, "onItemClick(): position:" + position + ",id:" + id);

            if (mDateDialog != null) {
                mDateDialog.dismiss();
                mDateDialog = null;
            }
            if (mTimeDialog != null) {
                mTimeDialog.dismiss();
                mTimeDialog = null;
            }
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
                mAlertDialog = null;
            }
            HashMap<String, String> obj = (HashMap<String, String>) mListView
                    .getItemAtPosition(position);
            String objId = obj.get("id");
            if (objId.equals(EXTRA_REPEAT_TYPE)) {
                mAlertDialog = new AlertDialog.Builder(mContext)
                        .setTitle("")
                        .setItems(mRepeatTypes, mRepeatClickedListener).create();
                mAlertDialog.show();
            } else if (objId.equals(REPEAT_WEEKLY)) {
                mAlertDialog = new AlertDialog.Builder(mContext)
                        .setTitle("")
                        .setItems(mWeeklyTypes, mWeeklyClickedListener).create();
                mAlertDialog.show();
            } else if (objId.equals(REPEAT_DATE)) {
                mDateDialog = new MyDatePickerDialog(mContext, mDateSetListener,
                        mDateTime.get(Calendar.YEAR), mDateTime.get(Calendar.MONTH),
                        mDateTime.get(Calendar.DAY_OF_MONTH));
                mDateDialog.show();
            } else if (objId.equals(REPEAT_TIME)) {
                mTimeDialog = new TimePickerDialog(mContext, mTimeSetListener,
                        mDateTime.get(Calendar.HOUR_OF_DAY), mDateTime.get(Calendar.MINUTE),
                        true);
                mTimeDialog.show();
            } else if (objId.equals(REPEAT_BIRTHDAY)) {
                // Start AddContactActivity.
                Intent intent = new Intent(mContext, AddContactActivity.class);
                intent.setAction(Intent.ACTION_PICK);
                intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
                if ((mContactDataIds != null) && (mContactDataIds.size() > 0)) {
                    intent.putExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS,
                            mContactDataIds);
                }
                intent.putExtra(AddContactActivity.EXTRA_BIRTHDAY_MODE, true);

                // Launch activity.
                startActivityForResult(intent, PICK_CONTACT);
            } else if (objId.equals(REPEAT_BULK)) {
                // Create contact list alert dialog and show it.
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                if (mAlertDialog != null) {
                    mAlertDialog.dismiss();
                }

                // Create contact list data.
                if (mContactItems != null && mContactItems.length > 0) {
                    String[] items = new String[mContactItems.length];
                    final boolean[] checkedItems = new boolean[items.length];
                    for (int i = 0; i < items.length; i++) {
                        items[i] = mContactItems[i].toString();
                        checkedItems[i] = false;
                    }

                    mAlertDialog = builder
                            .setTitle(getString(R.string.delete_contacts))
                            .setMultiChoiceItems(items, checkedItems,
                                    new DialogInterface.OnMultiChoiceClickListener() {

                                        @Override
                                        public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                            checkedItems[which] = isChecked;
                                        }
                                    })
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {

                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // Remove contacts.
                                            for (int i = checkedItems.length - 1; i >= 0; i--) {
                                                if (checkedItems[i]) {
                                                    mContactDataIds.remove(mContactItems[i]
                                                            .getDataId());
                                                }
                                            }

                                            // Update UI.
                                            setupBulkSchedule();
                                        }
                                    }).setNegativeButton(android.R.string.cancel, null).create();
                    mAlertDialog.show();
                }
            } else if (objId.equals(REPEAT_SOS)){
                // Create contact list alert dialog and show it.
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                if (mAlertDialog != null) {
                    mAlertDialog.dismiss();
                }

                // Create contact list data.
                if (mContactItems != null && mContactItems.length > 0) {
                    String[] items = new String[mContactItems.length];
                    final boolean[] checkedItems = new boolean[items.length];
                    for (int i = 0; i < items.length; i++) {
                        items[i] = mContactItems[i].toString();
                        checkedItems[i] = false;
                    }

                    mAlertDialog = builder
                            .setTitle(getString(R.string.delete_contacts))
                            .setMultiChoiceItems(items, checkedItems,
                                    new DialogInterface.OnMultiChoiceClickListener() {

                                        @Override
                                        public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                            checkedItems[which] = isChecked;
                                        }
                                    })
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {

                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // Remove contacts.
                                            for (int i = checkedItems.length - 1; i >= 0; i--) {
                                                if (checkedItems[i]) {
                                                    mContactDataIds.remove(mContactItems[i]
                                                            .getDataId());
                                                }
                                            }

                                            // Update UI.
                                            setupSosSchedule();
                                        }
                                    }).setNegativeButton(android.R.string.cancel, null).create();
                    mAlertDialog.show();
                }                
            } else if (objId.equals(EXTRA_SMS_CONTENT)) {
                // Start WriteSmsActivity.
                Intent intent = new Intent(mContext, WriteSmsActivity.class);
                intent.setAction(Intent.ACTION_EDIT);

                // Add contacts and the content.
                if ((mContactDataIds != null) && (mContactDataIds.size() > 0)) {
                    intent.putExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS,
                            mContactDataIds);
                }
                if (mSmsContent.length() > 0) {
                    intent.putExtra(WriteSmsActivity.EXTRA_SMS_CONTENT, mSmsContent);
                }

                if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
                    intent.putExtra(WriteSmsActivity.EXTRA_BULK_FILE_ID, mBulkFileId);
                } else if (mRepeatType == SCHEDULE_REPEAT_TYPE_BIRTHDAY) {
                    intent.putExtra(AddContactActivity.EXTRA_BIRTHDAY_MODE, true);
                }

                // Launch activity.
                startActivityForResult(intent, EDIT_SMS_CONTENT);
            }
        }
    };

    DialogInterface.OnClickListener mRepeatClickedListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mRepeatType = which;

            // Get current time.
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0); // Ignore second field.
            mDateTime = MainUtility.renewScheduleDatetime(getContentResolver(), mRepeatType, null,
                    mDateTime, now);
            setupTimerSchedule();
        }
    };

    DialogInterface.OnClickListener mWeeklyClickedListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mDateTime.set(Calendar.DAY_OF_WEEK, convertToDayOfWeek(which));

            // Get current time.
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0); // Ignore second field.
            mDateTime = MainUtility.renewScheduleDatetime(getContentResolver(), mRepeatType, null,
                    mDateTime, now);
            setupTimerSchedule();
        }
    };

    DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            mDateTime.set(year, monthOfYear, dayOfMonth);

            // Get current time.
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0); // Ignore second field.
            mDateTime = MainUtility.renewScheduleDatetime(getContentResolver(), mRepeatType, null,
                    mDateTime, now);
            if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
                setupBulkSchedule();
            } else {
                setupTimerSchedule();
            }
        }
    };

    TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener() {

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            mDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            mDateTime.set(Calendar.MINUTE, minute);

            // Get current time.
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0); // Ignore second field.
            mDateTime = MainUtility.renewScheduleDatetime(getContentResolver(), mRepeatType,
                    mContactDataIds, mDateTime, now);
            if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
                setupBulkSchedule();
            } else if (mRepeatType == SCHEDULE_REPEAT_TYPE_BIRTHDAY) {
                setupBirthdaySchedule();
            } else {
                setupTimerSchedule();
            }
        }
    };

    // Limit user only can choose three years for schedule.
    private class MyDatePickerDialog extends DatePickerDialog {
        private int mCurrentYear;

        public MyDatePickerDialog(Context context, OnDateSetListener callBack, int year,
                int monthOfYear, int dayOfMonth) {
            super(context, callBack, year, monthOfYear, dayOfMonth);
            mCurrentYear = Calendar.getInstance().get(Calendar.YEAR);
        }

        @Override
        public void onDateChanged(DatePicker view, int year, int month, int day) {
            if (year < mCurrentYear) {
                super.onDateChanged(view, mCurrentYear, month, day);
            } else if (year > mCurrentYear + 2) {
                super.onDateChanged(view, mCurrentYear + 2, month, day);
            } else {
                super.onDateChanged(view, year, month, day);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mContext = this;

        // Set UI layout.
        setContentView(R.layout.activity_edit_schedule);

        Intent intent = getIntent();
        mScheduleDateFormat = DBInternalHelper.getScheduleDateFormat();
        mRepeatTypes = getResources().getStringArray(R.array.schedule_repeat_types);
        mWeeklyTypes = createWeeklyTypes();

        // Get schedule id.
        mScheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1);

        // Get repeat type.
        mRepeatType = intent.getIntExtra(EXTRA_REPEAT_TYPE, SCHEDULE_REPEAT_TYPE_NONE);

        // Get date and time.
        mDateTime = Calendar.getInstance();
        if (intent.hasExtra(EXTRA_REPEAT_DATETIME)) {
            try {
                Date date = mScheduleDateFormat.parse(intent.getStringExtra(EXTRA_REPEAT_DATETIME));
                mDateTime.setTime(date);
            } catch (ParseException e) {
                // Can not parse string, use current time.
            }
        }
        mDateTime.set(Calendar.SECOND, 0); // Ignore second field.
        mOriginalDateTime = (Calendar) mDateTime.clone();

        // Setup schedule name and status.
        if (intent.hasExtra(EXTRA_SCHEDULE_NAME)) {
            EditText editView = (EditText) findViewById(R.id.editTextName);
            editView.setText(intent.getCharSequenceExtra(EXTRA_SCHEDULE_NAME));
        }

        // Default status is enable, except BULK schedule.
        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButtonStatus);
        long status;
        if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
            status = intent.getLongExtra(EXTRA_SCHEDULE_STATUS, 0);
        } else {
            status = intent.getLongExtra(EXTRA_SCHEDULE_STATUS, 1);
        }
        toggle.setChecked((status == 1));

        // Get contacts and the SMS content.
        if (intent.hasExtra(EXTRA_CONTACT_DATA_IDS)) {
            mContactDataIds = (HashSet<Long>) intent.getSerializableExtra(EXTRA_CONTACT_DATA_IDS);
        }
        if (intent.hasExtra(EXTRA_SMS_CONTENT)) {
            mSmsContent = intent.getCharSequenceExtra(EXTRA_SMS_CONTENT);
        } else {
            mSmsContent = "";
        }
        mBulkFileId = intent.getLongExtra(EXTRA_BULK_FILE_ID, -1);

        // Setup action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Setup adapter for timer UI or birthday UI.
        mListView = (ListView) findViewById(android.R.id.list);
        if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
            // Setup bulk schedule.
            setTitle(getString(R.string.bulk_schedule));
            setupBulkSchedule();
        } else if (mRepeatType == SCHEDULE_REPEAT_TYPE_BIRTHDAY) {
            // Setup birthday schedule.
            setTitle(getString(R.string.birthday_schedule));
            setupBirthdaySchedule();
        } else if (mRepeatType == SCHEDULE_REPEAT_TYPE_SOS) {
            // Setup SOS schedule.
            setTitle(getString(R.string.sos_schedule));
            EditText editView = (EditText) findViewById(R.id.editTextName);
            editView.setEnabled(false);
            editView.setText(R.string.sos_schedule);
            
            mDateTime.setTimeInMillis(0);
            mOriginalDateTime.setTimeInMillis(0);

            setupSosSchedule();      
        } else {
            // Setup timer schedule.
            setTitle(getString(R.string.timer_schedule));
            setupTimerSchedule();
        }

        mListView.setOnItemClickListener(mItemClickedListener);

        // Workaround for not auto-focus on EditText.
        if (mScheduleId > -1) {
            mListView.requestFocus();
        }
    }

    @Override
    protected void onStop() {
        if (mDateDialog != null) {
            mDateDialog.dismiss();
            mDateDialog = null;
        }
        if (mTimeDialog != null) {
            mTimeDialog.dismiss();
            mTimeDialog = null;
        }
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_edit_schedule, menu);

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
                return true;
            case R.id.itemSend:
                return onSaveMenuClicked(item, true);
            case R.id.itemSave:
                return onSaveMenuClicked(item, false);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBackPressed() {
        Intent intent = getIntent();

        // Check repeat type.
        boolean isChanged = (mBulkFileId >= 0) ? true : false;
        if (intent.getIntExtra(EXTRA_REPEAT_TYPE, SCHEDULE_REPEAT_TYPE_NONE) != mRepeatType) {
            isChanged = true;
        }

        // Check repeat date and time.
        if (!isChanged) {
            isChanged = !mDateTime.equals(mOriginalDateTime);
        }

        // Check schedule name.
        if (!isChanged) {
            CharSequence scheduleName = "";
            if (intent.hasExtra(EXTRA_SCHEDULE_NAME)) {
                scheduleName = intent.getCharSequenceExtra(EXTRA_SCHEDULE_NAME);
            }

            EditText editView = (EditText) findViewById(R.id.editTextName);
            isChanged = !editView.getText().toString().equals(scheduleName);
        }

        // Check schedule status.
        if (!isChanged) {
            long status;
            if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
                status = getIntent().getLongExtra(EXTRA_SCHEDULE_STATUS, 0);
            } else {
                status = getIntent().getLongExtra(EXTRA_SCHEDULE_STATUS, 1);
            }
            ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButtonStatus);
            long toggleStatus = toggle.isChecked() ? 1 : 0;
            isChanged = (toggleStatus != status);
        }

        // Check SMS contacts.
        if (!isChanged) {
            HashSet<Long> contactDataIds = null;
            if (intent.hasExtra(EXTRA_CONTACT_DATA_IDS)) {
                contactDataIds = (HashSet<Long>) intent
                        .getSerializableExtra(EXTRA_CONTACT_DATA_IDS);
            }

            if (mContactDataIds == null) {
                if (contactDataIds != null) {
                    isChanged = true;
                }
            } else {
                if (contactDataIds == null) {
                    isChanged = true;
                } else {
                    isChanged = !mContactDataIds.equals(contactDataIds);
                }
            }
        }

        // Check SMS content.
        if (!isChanged) {
            CharSequence smsContent = "";
            if (intent.hasExtra(EXTRA_SMS_CONTENT)) {
                smsContent = intent.getCharSequenceExtra(EXTRA_SMS_CONTENT);
            }

            isChanged = !mSmsContent.equals(smsContent);
        }

        // Back to MainActivity or EditScheduleActivity.
        if (isChanged) {
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }
            mAlertDialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.msg_schedule_is_changed))
                    .setPositiveButton(R.string.yes,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Save the schedule.
                                    onSaveMenuClicked(null, false);
                                }
                            })
                    .setNegativeButton(R.string.no,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Finish the activity.
                                    Intent result = new Intent();
                                    result.putExtra(EXTRA_BULK_FILE_ID, mBulkFileId);
                                    setResult(RESULT_CANCELED, result);
                                    finish();
                                }
                            })
                    .create();
            mAlertDialog.show();
        } else {
            // Finish the activity.
            Intent result = new Intent();
            result.putExtra(EXTRA_BULK_FILE_ID, mBulkFileId);
            setResult(RESULT_CANCELED, result);
            finish();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_CONTACT) {
            if ((resultCode == RESULT_OK) && (data != null)) {
                mContactDataIds = (HashSet<Long>) data
                        .getSerializableExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS);

                // Refresh UI with the result.
                if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
                    setupBulkSchedule();
                } else if (mRepeatType == SCHEDULE_REPEAT_TYPE_BIRTHDAY) {
                    // Get current time.
                    Calendar now = Calendar.getInstance();
                    now.set(Calendar.SECOND, 0); // Ignore second field.
                    mDateTime = MainUtility.renewScheduleDatetime(getContentResolver(),
                            mRepeatType, mContactDataIds, mDateTime, now);
                    setupBirthdaySchedule();
                } else {
                    setupTimerSchedule();
                }
            }
        } else if (requestCode == EDIT_SMS_CONTENT) {
            if ((resultCode == RESULT_OK) && (data != null)) {
                // Setup contacts.
                if (data.hasExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS)) {
                    mContactDataIds = (HashSet<Long>) data
                            .getSerializableExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS);
                }

                // Setup the SMS content.
                if (data.hasExtra(WriteSmsActivity.EXTRA_SMS_CONTENT)) {
                    mSmsContent = data.getCharSequenceExtra(WriteSmsActivity.EXTRA_SMS_CONTENT);
                }

                // Refresh UI with the result.
                if (mRepeatType == SCHEDULE_REPEAT_TYPE_BULK) {
                    setupBulkSchedule();
                } else if (mRepeatType == SCHEDULE_REPEAT_TYPE_BIRTHDAY) {
                    // Get current time.
                    Calendar now = Calendar.getInstance();
                    now.set(Calendar.SECOND, 0); // Ignore second field.
                    mDateTime = MainUtility.renewScheduleDatetime(getContentResolver(),
                            mRepeatType, mContactDataIds, mDateTime, now);
                    setupBirthdaySchedule();
                } else if (mRepeatType == SCHEDULE_REPEAT_TYPE_SOS) {
                    setupSosSchedule();
                } else {
                    setupTimerSchedule();
                }
            }
        }
    }

    public boolean onSaveMenuClicked(MenuItem item, boolean isSendDirectly) {
        // Return the selected contacts.
        Intent intent = new Intent();
        int resultCode = RESULT_OK;
        String resultMsg = "";

        // Put schedule id.
        intent.putExtra(EXTRA_SCHEDULE_ID, mScheduleId);

        // Put schedule status.
        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButtonStatus);
        long status = toggle.isChecked() ? 1 : 0;
        if (mRepeatType == SCHEDULE_REPEAT_TYPE_NONE) {
            Calendar now = Calendar.getInstance();
            now.set(Calendar.SECOND, 0); // Ignore second field.
            if (mDateTime.before(now)) {
                // Out-of-date schedule, deactivate it.
                status = 0;
            }
        }
        intent.putExtra(EXTRA_SCHEDULE_STATUS, status);

        // Put repeat type, date and time.
        intent.putExtra(EXTRA_REPEAT_TYPE, mRepeatType);
        intent.putExtra(EXTRA_REPEAT_DATETIME, mScheduleDateFormat.format(mDateTime.getTime()));

        // Put contact data IDs and SMS content.
        if ((mContactDataIds != null) && (mContactDataIds.size() > 0)) {
            intent.putExtra(EXTRA_CONTACT_DATA_IDS, mContactDataIds);
        } else {
            resultCode = RESULT_CANCELED;
            resultMsg = getString(R.string.msg_input_sms);
        }
        if (mSmsContent.length() > 0) {
            intent.putExtra(EXTRA_SMS_CONTENT, mSmsContent);
        } else {
            resultCode = RESULT_CANCELED;
            resultMsg = getString(R.string.msg_input_sms);
        }

        // Put bulk file id.
        intent.putExtra(EXTRA_BULK_FILE_ID, mBulkFileId);

        // Put schedule name.
        EditText editView = (EditText) findViewById(R.id.editTextName);
        if (editView.getText().length() > 0) {
            intent.putExtra(EXTRA_SCHEDULE_NAME, editView.getText());
        } else {
            resultCode = RESULT_CANCELED;
            resultMsg = getString(R.string.msg_input_schedule_name);
        }
        
        // Put "Send Directly" flag. 
        intent.putExtra(EXTRA_SEND_DIRECTLY, isSendDirectly);

        // Back to ListScheduleActivity.
        if (resultCode == RESULT_OK) {
            setResult(resultCode, intent);
            finish();
        } else {
            Toast.makeText(this, resultMsg, Toast.LENGTH_SHORT).show();
        }

        return true;
    }

    /**
     * Populate the contact list based on account currently selected in the
     * account spinner.
     */
    private void setupTimerSchedule() {
        ArrayList<Map<String, String>> items = new ArrayList<Map<String, String>>();
        HashMap<String, String> item;

        // Setup repeat type.
        item = new HashMap<String, String>();
        item.put("id", EXTRA_REPEAT_TYPE);
        item.put("text1", getString(R.string.repeat));
        item.put("text2", mRepeatTypes[mRepeatType]);
        items.add(item);

        // Setup date.
        item = new HashMap<String, String>();
        if (mRepeatType == SCHEDULE_REPEAT_TYPE_WEEKLY) {
            int which = convertToWeeklyTypes(mDateTime.get(Calendar.DAY_OF_WEEK));
            item.put("id", REPEAT_WEEKLY);
            item.put("text1", getString(R.string.date));
            item.put("text2", mWeeklyTypes[which]);
            items.add(item);
        } else if (mRepeatType != SCHEDULE_REPEAT_TYPE_DAILY) {
            item.put("id", REPEAT_DATE);
            item.put("text1", getString(R.string.date));
            item.put("text2", DateFormat.getDateInstance().format(mDateTime.getTime()));
            items.add(item);
        }

        // Setup date and time.
        item = new HashMap<String, String>();
        item.put("id", REPEAT_TIME);
        item.put("text1", getString(R.string.time));
        item.put("text2", DateFormat.getTimeInstance(DateFormat.SHORT).format(mDateTime.getTime()));
        items.add(item);

        // Setup SMS.
        item = new HashMap<String, String>();
        item.put("id", EXTRA_SMS_CONTENT);
        item.put("text1", getString(R.string.short_message));
        item.put("text2", mSmsContent.toString());
        items.add(item);

        // Setup adapter.
        SimpleAdapter adapter = new SimpleAdapter(this,
                items, R.layout.schedule_setting_entry,
                new String[] {
                        "text1",
                        "text2"
                },
                new int[] {
                        R.id.listText1,
                        R.id.listText2
                });
        mListView.setAdapter(adapter);
    }

    private void setupBirthdaySchedule() {
        ArrayList<Map<String, String>> items = new ArrayList<Map<String, String>>();
        HashMap<String, String> item;

        // Setup time.
        item = new HashMap<String, String>();
        item.put("id", REPEAT_TIME);
        item.put("text1", getString(R.string.time));
        item.put("text2", DateFormat.getTimeInstance(DateFormat.SHORT).format(mDateTime.getTime()));
        items.add(item);

        // Setup contacts.
        item = new HashMap<String, String>();
        item.put("id", REPEAT_BIRTHDAY);
        item.put("text1", getString(R.string.birthday));
        StringBuffer sb = new StringBuffer(128);
        if ((mContactDataIds != null) && (mContactDataIds.size() > 0)) {
            HashSet<Long> contactIds = ContactUtility.convertDataIdToContactId(
                    getContentResolver(), Phone.CONTENT_ITEM_TYPE, mContactDataIds);

            int count = 0;
            ArrayList<ContactBirthday> cbList = ContactUtility.getBirthdays(getContentResolver(),
                    contactIds);
            for (ContactBirthday cb : cbList) {
                if (sb.length() > 0) {
                    sb.append(System.getProperty("line.separator"));
                }
                if (count >= 7) {
                    sb.append(".....");
                    break;
                }
                sb.append(cb.displayName);
                sb.append(" (");
                sb.append(cb.startDate);
                sb.append(")");
                count++;
            }
        }
        item.put("text2", sb.toString());
        items.add(item);

        // Setup SMS.
        item = new HashMap<String, String>();
        item.put("id", EXTRA_SMS_CONTENT);
        item.put("text1", getString(R.string.short_message));
        item.put("text2", mSmsContent.toString());
        items.add(item);

        // Setup adapter.
        SimpleAdapter adapter = new SimpleAdapter(this,
                items, R.layout.schedule_setting_entry,
                new String[] {
                        "text1",
                        "text2"
                },
                new int[] {
                        R.id.listText1,
                        R.id.listText2
                });
        mListView.setAdapter(adapter);
    }

    private void setupBulkSchedule() {
        ArrayList<Map<String, String>> items = new ArrayList<Map<String, String>>();
        HashMap<String, String> item;

        // Setup date.
        item = new HashMap<String, String>();
        item.put("id", REPEAT_DATE);
        item.put("text1", getString(R.string.date));
        item.put("text2", DateFormat.getDateInstance().format(mDateTime.getTime()));
        items.add(item);

        // Setup time.
        item = new HashMap<String, String>();
        item.put("id", REPEAT_TIME);
        item.put("text1", getString(R.string.time));
        item.put("text2", DateFormat.getTimeInstance(DateFormat.SHORT).format(mDateTime.getTime()));
        items.add(item);

        // Setup contacts.
        generateContactItems();

        item = new HashMap<String, String>();
        item.put("id", REPEAT_BULK);
        item.put("text1", getString(R.string.contacts));
        StringBuffer sb = new StringBuffer(128);
        if ((mContactDataIds != null) && (mContactDataIds.size() > 0)) {
            Cursor cursor = ContactUtility.getPhoneCursor(getContentResolver(), mContactDataIds);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        if (sb.length() > 0) {
                            sb.append(System.getProperty("line.separator"));
                        }
                        if (cursor.getPosition() >= 7) {
                            sb.append(".....");
                            break;
                        }
                        sb.append(cursor.getString(cursor
                                .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        item.put("text2", sb.toString());
        items.add(item);

        // Setup SMS.
        item = new HashMap<String, String>();
        item.put("id", EXTRA_SMS_CONTENT);
        item.put("text1", getString(R.string.short_message));
        item.put("text2", mSmsContent.toString());
        items.add(item);

        // Setup adapter.
        SimpleAdapter adapter = new SimpleAdapter(this,
                items, R.layout.schedule_setting_entry,
                new String[] {
                        "text1",
                        "text2"
                },
                new int[] {
                        R.id.listText1,
                        R.id.listText2
                });
        mListView.setAdapter(adapter);
    }
    
    private void setupSosSchedule() {
        ArrayList<Map<String, String>> items = new ArrayList<Map<String, String>>();
        HashMap<String, String> item;

        // Setup contacts.
        generateContactItems();
        
        item = new HashMap<String, String>();
        item.put("id", REPEAT_SOS);
        item.put("text1", getString(R.string.contacts));
        StringBuffer sb = new StringBuffer(128);
        if ((mContactDataIds != null) && (mContactDataIds.size() > 0)) {
            Cursor cursor = ContactUtility.getPhoneCursor(getContentResolver(), mContactDataIds);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        if (sb.length() > 0) {
                            sb.append(System.getProperty("line.separator"));
                        }
                        if (cursor.getPosition() >= 7) {
                            sb.append(".....");
                            break;
                        }
                        sb.append(cursor.getString(cursor
                                .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        item.put("text2", sb.toString());
        items.add(item);

        // Setup SMS.
        item = new HashMap<String, String>();
        item.put("id", EXTRA_SMS_CONTENT);
        item.put("text1", getString(R.string.short_message));
        item.put("text2", mSmsContent.toString());
        items.add(item);

        // Setup adapter.
        SimpleAdapter adapter = new SimpleAdapter(this,
                items, R.layout.schedule_setting_entry,
                new String[] {
                        "text1",
                        "text2"
                },
                new int[] {
                        R.id.listText1,
                        R.id.listText2
                });
        mListView.setAdapter(adapter);
    }    

    @SuppressLint("SimpleDateFormat")
    private String[] createWeeklyTypes() {
        String[] weeklyTypes = new String[7];
        SimpleDateFormat weeklyFormat = new SimpleDateFormat("EEEE");
        Calendar c = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            c.set(Calendar.DAY_OF_WEEK, convertToDayOfWeek(i));
            weeklyTypes[i] = weeklyFormat.format(c.getTime());
        }

        return weeklyTypes;
    }

    private int convertToDayOfWeek(int which) {
        switch (which) {
            case 0:
                return Calendar.SUNDAY;
            case 1:
                return Calendar.MONDAY;
            case 2:
                return Calendar.TUESDAY;
            case 3:
                return Calendar.WEDNESDAY;
            case 4:
                return Calendar.THURSDAY;
            case 5:
                return Calendar.FRIDAY;
            case 6:
                return Calendar.SATURDAY;
            default:
                return Calendar.SUNDAY;
        }
    }

    private int convertToWeeklyTypes(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.SUNDAY:
                return 0;
            case Calendar.MONDAY:
                return 1;
            case Calendar.TUESDAY:
                return 2;
            case Calendar.WEDNESDAY:
                return 3;
            case Calendar.THURSDAY:
                return 4;
            case Calendar.FRIDAY:
                return 5;
            case Calendar.SATURDAY:
                return 6;
            default:
                return 0;
        }
    }

    private void generateContactItems() {
        if ((mContactDataIds != null) && (mContactDataIds.size() > 0)) {
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
        } else {
            mContactItems = null;
        }
    }
}
