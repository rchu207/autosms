
package tw.idv.rchu.autosms;

import java.io.File;

class DocumentRecord {
    private String description;
    private boolean isDirectory;
    private boolean isBack;
    private File file;
    private long imageId;

    public DocumentRecord(String description, File file, long imageId) {
        this.description = description;
        this.file = file;

        if (file == null || file.isDirectory()) {
            this.isDirectory = true;

            if (imageId >= 0) {
                this.imageId = imageId;
            } else {
                this.imageId = R.drawable.ic_folder;
            }
        } else {
            this.isDirectory = false;
            this.imageId = imageId;
        }

        this.isBack = false;
    }    

    public DocumentRecord(String description, long imageId) {
        this.description = description;
        this.imageId = imageId;

        this.isDirectory = true;
        if (imageId >= 0) {
            this.isBack = true;
        } else {
            this.isBack = false;
        }
    }

    public String getDescription() {
        if (description.length() > 0) {
            return description;
        } else if (file != null) {
            return file.getName();
        } else {
            return "";
        }
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean isBack() {
        return isBack;
    }

    public File getFile() {
        return file;
    }

    public long getImageId() {
        return imageId;
    }
}
