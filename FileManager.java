package com.example.blackbox;

import android.os.Environment;
import android.util.Log;

import org.ejml.simple.SimpleMatrix;

import java.io.File;
import java.io.IOException;

 class FileManager  {

     private String TAG = "FileManager";
     private boolean mFilesCreatedFlag;

     /**
      * Saves the Data on a CSV file in external storage, if the file name exists its overeaten.
      * @param data The data stored in a SimpleMatrix.
      * @param fileName The name of the file is going to ve saved on (Ex: "filename.csv").
      * @return True if the data has been stored successfully.
      * False it the storage wasn't writable or the folders weren't created.
      */
    boolean saveDataToFile(SimpleMatrix data, String fileName) {
        if (isExternalStorageWritable() && mFilesCreatedFlag) {
            /* Gets External Storage Path */
            File filePath = new File(Environment.getExternalStorageDirectory(), fileName);
            try {
                /* Tries saving the matrix on External Storage */
                data.saveToFileCSV(filePath.toString());
                Log.d(TAG, "saveDataToFile: File saved successfully");
                return true;

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            Log.d(TAG, "saveDataToFile: Couldn't save file");
            return false;
        }
    }

     /**
      * Generates the path to the video.mp4 file where the video is going to be saved.
      * @return the path to video.mp4, or null if the folder doesn't exist.
      */
    String createVideoFile() {
        if(mFilesCreatedFlag) {
            File filePath = new File(Environment.getExternalStorageDirectory()
                                             + "/BlackBox/Videos", "video.mp4");
            return filePath.getAbsolutePath();
        } else {
            Log.d(TAG, "createVideoFile: Folders doesn't exist");
            return null;
        }
    }

     /**
      * Creates a folder on the external storage of the phone.
      * @param folderName The name of the folder to be created.
      * @return True if the folder exists or has been created.
      * False if it didn't exist and it couldn't be created.
      */
    boolean createFolder(String folderName){
        String mFolder = Environment.getExternalStorageDirectory() + "/" + folderName;
        File f = new File(mFolder);
        if(!f.exists()) {
            if(!f.mkdir()){
                /* If we can't create it and it doesn't exist, failure */
                Log.d(TAG, "createFolder: Couldn't create the Directory");
                mFilesCreatedFlag = false;
                return false;
            }
            /* It didn't exist but we created it, success */
            Log.d(TAG, "createFolder: Directory created succesfully");
            mFilesCreatedFlag = true;
            return true;
        } else {
            /* If the file exists, success */
            Log.d(TAG, "createFolder: Directory already existed");
            mFilesCreatedFlag = true;
            return true;
        }
     }

     /**
      * Checks if we can write in the internal storage.
      * @return True if the internal storage is writable.
      * False if the internal storage isn't writable.
      */
    private boolean isExternalStorageWritable() {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.i("State", "Yes, it is writable!");
            return true;
        } else {
            Log.i("State", "No, it isn't writable!");
            return false;
        }
    }
}
