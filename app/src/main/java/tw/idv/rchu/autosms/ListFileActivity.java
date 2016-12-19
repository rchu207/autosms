
package tw.idv.rchu.autosms;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.NoSuchElementException;

public class ListFileActivity extends ActionBarActivity {
    static final String TAG = "[iSend]ListFileActivity";
    
    private ListView mListView;

    private DocumentListAdapter mDocumentAdapter;

    private class DocumentListAdapter extends BaseAdapter implements Comparator<File> {
        ArrayList<String> mExtensions = new ArrayList<String>(16);
        HashMap<String, Integer> mExtensionIcons = new HashMap<String, Integer>(16);
        ArrayList<DocumentRecord> mDocuments = new ArrayList<DocumentRecord>();
        ArrayList<DocumentRecord> mHistory = new ArrayList<DocumentRecord>();        

        public DocumentListAdapter() {
            // Create supported file extensions.
            mExtensions.add("csv");
            mExtensionIcons.put("csv", R.drawable.ic_file_csv);
        }

        @Override
        public int getCount() {
            return mDocuments.size();
        }

        @Override
        public Object getItem(int position) {
            return mDocuments.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                convertView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }

            DocumentRecord record = (DocumentRecord) getItem(position);

            TextView title = (TextView) convertView.findViewById(android.R.id.text1);
            title.setText(record.getDescription());
            if (record.getImageId() >= 0) {
                title.setCompoundDrawablesWithIntrinsicBounds(parent.getContext().getResources()
                        .getDrawable((int) record.getImageId()), null, null, null);
            } else {
                title.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }

            return convertView;
        }

        @Override
        public int compare(File a, File b) {
            if (a.isDirectory() && !b.isDirectory()) {
                return -1;
            } else if (!a.isDirectory() && b.isDirectory()) {
                return 1;
            } else {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        };

        private final FileFilter rootFilter = new FileFilter() {

            @Override
            public boolean accept(File pathName) {
                if (pathName.isHidden()) {
                    return false;
                } else if (pathName.isDirectory() && pathName.canRead()) {
                    String fileName = pathName.getName().toLowerCase(Locale.US);
                    if (fileName.equals("sdcard")) {
                        // It is same path as
                        // getExternalStoragePublicDirectory("").
                        return false;
                    } else if (fileName.contains("sd") || fileName.contains("card")
                            || fileName.contains("usb")) {
                        return true;
                    } else {
                        return false;
                    }
                }

                return false;
            }
        };

        private final FileFilter fileFilter = new FileFilter() {

            @Override
            public boolean accept(File pathName) {
                if (pathName.isHidden()) {
                    return false;
                } else if (pathName.isDirectory()) {
                    return true;
                } else {
                    String fileExtension = getFileExtension(pathName.getName());
                    for (String extension : mExtensions) {
                        if (fileExtension.equals(extension)) {
                            return true;
                        }
                    }
                    
                    return false;
                }
            }
        };

        public void showRoot() {
            // Update browsing document history.
            mHistory.clear();
            mHistory.add(new DocumentRecord(getString(R.string.storage), null, -1));

            // Get device storage.
            mDocuments.clear();
            mDocuments.add(new DocumentRecord(getString(R.string.device_storage), 
                    Environment.getExternalStoragePublicDirectory(""),
                    R.drawable.ic_storage));

            // Get SD card and USB storage.
            File[] mntFolders = new File[2];
            mntFolders[0] = new File("/mnt");
            mntFolders[1] = new File("/Removable"); // ASUS TF700T

            for (File folder : mntFolders) {
                File[] files = folder.listFiles(rootFilter);
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().contains("usb")) {
                            mDocuments.add(new DocumentRecord("", file, R.drawable.ic_usb_storage));
                        } else {
                            mDocuments.add(new DocumentRecord("", file, R.drawable.ic_sd_storage));
                        }                    
                    }
                }                
            }

            // If only one root, then show its children.
            if (mDocuments.size() == 1) {
                File directory = mDocuments.get(0).getFile();

                // Update DocumentRecord.
                mDocuments.clear();

                // List files inside the folder and sort them.
                listFiles(directory);
            }

            // Notify data is changed to UI components.
            notifyDataSetChanged();
        }

        public void showLocalDocuments(DocumentRecord record) {
            // Add default "Back" item.
            mDocuments.clear();
            DocumentRecord top = top();
            String description = "";
            if (top != null) {
                description = String.format(getString(R.string.up_to_parent), top.getDescription());
            }       
            mDocuments.add(new DocumentRecord(description, R.drawable.ic_action_previous_item));

            // Update browsing document history.
            mHistory.add(record);

            // List files inside the folder and sort them.
            listFiles(record.getFile());

            // Notify data is changed to UI components.
            notifyDataSetChanged();
        }

        private void listFiles(File directory) {
            // List files inside the folder and sort them.
            File[] files = directory.listFiles(fileFilter);
            if (files != null) {
                Arrays.sort(files, this);
                for (File file : files) {
                    String extension = getFileExtension(file.getName());
                    int icon = -1;
                    if (mExtensionIcons.containsKey(extension)) {
                        icon = mExtensionIcons.get(extension);
                    }

                    mDocuments.add(new DocumentRecord("", file, icon));
                }
            }
        }

        public DocumentRecord top() {
            if (mHistory.size() <= 0) {
                return null;
            }
            return mHistory.get(mHistory.size()-1);
        }
        
        private DocumentRecord pop() {
            if (mHistory.size() <= 0) {
                return null;
            }
            return mHistory.remove(mHistory.size() - 1);
        }

        public boolean moveBack() {
            DocumentRecord record = null;
            try {
                pop();
                record = pop();
            } catch (NoSuchElementException e) {
                // No more history to go back.
                return false;
            }

            // Show the documents.
            if (record == null) {
                return false;
            } else if (record.getFile() == null) {
                // When move back in recent files, it also shows root.
                showRoot();
            } else {
                showLocalDocuments(record);
            }

            return true;
        }
        
        private String getFileExtension(String name) {
            if (name == null || name.length() == 0) {
                return "";
            }
            name = name.toLowerCase(Locale.US);
            
            int index = name.lastIndexOf(".") + 1;
            if (index > 0 && index < name.length()) {
                return name.substring(index);
            } else {
                return "";
            }
        }
    }
    
    private AdapterView.OnItemClickListener mListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Set action bar's title.
            DocumentRecord record = (DocumentRecord) mDocumentAdapter.getItem(position);
            getSupportActionBar().setTitle(record.getDescription());

            // Show files inside the folder or back to MainActivity with the
            // selected document.
            if (record.isBack()) {
                moveBack();
            } else if (record.isDirectory()) {
                mDocumentAdapter.showLocalDocuments(record);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.fromFile(record.getFile()));
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    };    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_schedule);
        
        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(mListener);

        // Create adapters.
        mDocumentAdapter = new DocumentListAdapter();
        mListView.setAdapter(mDocumentAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Display root.
        mDocumentAdapter.showRoot();

        // Update action bar's title.
        DocumentRecord record = mDocumentAdapter.top();
        getSupportActionBar().setTitle(record.getDescription());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_list_file, menu);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() == R.id.itemClose) {
                MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            } else {
                MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
            }
        }

        return super.onCreateOptionsMenu(menu);
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.itemClose:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public void onBackPressed() {
        if (!moveBack()) {
            super.onBackPressed();
        }
    }    

    private boolean moveBack() {
        // Move back browsing history.
        if ((mDocumentAdapter != null) && mDocumentAdapter.moveBack()) {
            DocumentRecord record = mDocumentAdapter.top();
            getSupportActionBar().setTitle(record.getDescription());
            return true;
        } else {
            return false;
        }
    }
}
