package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.app.backup.BackupManager;
import android.content.Context;
import android.widget.Toast;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.R;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.crypto.SecretKey;

public class DatabaseHelper {

    static final Object DatabaseFileLock = new Object();

    public static void wipeDatabase(Context context) {
        File db = new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE);
        File dbBackup = new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE_BACKUP);
        db.delete();
        dbBackup.delete();
    }

    private static void copyFile(File src, File dst)
            throws IOException {
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        }
    }

    public static boolean backupDatabase(Context context) {
        File original = new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE);
        File backup = new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE_BACKUP);

        if (original.exists()) {
            try {
                copyFile(original, backup);
            } catch (IOException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean restoreDatabaseBackup(Context context) {
        File original = new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE);
        File backup = new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE_BACKUP);

        if (backup.exists()) {
            try {
                copyFile(backup, original);
            } catch (IOException e) {
                return false;
            }
        }

        return true;
    }

    /* Database functions */
    public static boolean saveDatabase(Context context, ArrayList<Entry> entries, SecretKey encryptionKey) {
        if (encryptionKey == null) {
            Toast.makeText(context, R.string.toast_encryption_key_empty, Toast.LENGTH_LONG).show();
            return false;
        }

        String jsonString = entriesToString(entries);

        try {
            synchronized (DatabaseHelper.DatabaseFileLock) {
                byte[] data = EncryptionHelper.encrypt(encryptionKey, jsonString.getBytes());

                FileHelper.writeBytesToFile(new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE), data);
            }
        } catch (Exception error) {
            error.printStackTrace();
            return false;
        }

        BackupManager backupManager = new BackupManager(context);
        backupManager.dataChanged();

        return true;
    }

    public static ArrayList<Entry> loadDatabase(Context context, SecretKey encryptionKey) {
        ArrayList<Entry> entries = new ArrayList<>();

        if (encryptionKey != null) {
            try {
                synchronized (DatabaseHelper.DatabaseFileLock) {
                    byte[] data = FileHelper.readFileToBytes(new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE));
                    data = EncryptionHelper.decrypt(encryptionKey, data);

                    entries = stringToEntries(new String(data));
                }
            } catch (Exception error) {
                error.printStackTrace();
            }
        } else {
            Toast.makeText(context, R.string.toast_encryption_key_empty, Toast.LENGTH_LONG).show();
        }

        return entries;
    }

    /* Conversion functions */

    public static String entriesToString(ArrayList<Entry> entries) {
        JSONArray json = new JSONArray();

        for(Entry e: entries){
            try {
                json.put(e.toJSON());
            } catch (Exception error) {
                error.printStackTrace();
            }
        }

        return json.toString();
    }

    public static ArrayList<Entry> stringToEntries(String data) {
        ArrayList<Entry> entries = new ArrayList<>();

        try {
            JSONArray json = new JSONArray(data);

            for (int i = 0; i < json.length(); i++) {
                Entry entry = new Entry(json.getJSONObject(i));
                entries.add(entry);
            }
        } catch (Exception error) {
            error.printStackTrace();
        }

        return entries;
    }
}