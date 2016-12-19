
package tw.idv.rchu.autosms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class CsvFetcher {
    
    public static final int MAX_TAG_COUNT = 6;
    
    class CsvRecord {
        public long dataId;
        public String phone;
        public String[] tags;
        
        public CsvRecord() {
            dataId = -1;
            phone = "";
            tags = new String[0];
        }
    }
    
    ArrayList<CsvRecord> mRecords = new ArrayList<CsvRecord>(20);

    public void parse(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String data = "";
            while ((data = reader.readLine()) != null) {
                CsvRecord record = new CsvRecord();
                String[] dataArray = data.split(",");
                if (dataArray.length < 2) {
                    continue;
                }
                
                // Get phone number.
                record.phone = dataArray[0].trim();
                
                // Get iSend tags.
                int tagCount = dataArray.length - 1;
                if (tagCount > MAX_TAG_COUNT) {
                    tagCount = MAX_TAG_COUNT; 
                }
                record.tags = new String[tagCount];
                for (int i = 0; i < tagCount; i++) {
                    record.tags[i] = dataArray[i+1].trim();
                }
                
                mRecords.add(record);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public int getCount() {
        return mRecords.size();
    }
    
    public CsvRecord getItem(int index) {
        if (index < 0 || index >= mRecords.size()) {
            return null;
        }
        
        return mRecords.get(index);
    }
    
    public ArrayList<CsvRecord> getItems() {
        return mRecords;
    }
}
