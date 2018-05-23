package com.gigabytedevelopersinc.app.CometOTP.Utilities;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class FileHelper {
    public static String readFileToString(Context context, Uri file) {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            reader.close();
            inputStream.close();
        } catch (Exception error) {
            error.printStackTrace();
        }

        return(stringBuilder.toString());
    }

    public static boolean writeStringToFile(Context context, Uri file, String content) {
        boolean success = true;

        try {
            OutputStream outputStream = context.getContentResolver().openOutputStream(file);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(content);
            writer.close();
            outputStream.close();
        } catch (Exception error) {
            success = false;
            error.printStackTrace();
        }

        return success;
    }

    public static byte[] readFileToBytes(File file) throws IOException {
        final InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            in.close();
        }
    }

    public static void writeBytesToFile(File file, byte[] data) throws IOException {
        final OutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    public static byte[] readFileToBytes(Context context, Uri file) throws IOException {
        final InputStream in = context.getContentResolver().openInputStream(file);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            in.close();
        }
    }

    public static void writeBytesToFile(Context context, Uri file, byte[] data) throws IOException {
        final OutputStream out = context.getContentResolver().openOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    public static String backupFilename(Context context, Constants.BackupType type) {
        Settings settings = new Settings(context);
        switch (type) {
            case PLAIN_TEXT:
                if (settings.getIsAppendingDateTimeToBackups()) {
                    return String.format(Constants.BACKUP_FILENAME_PLAIN_FORMAT, Tools.getDateTimeString());
                } else {
                    return Constants.BACKUP_FILENAME_PLAIN;
                }
            case ENCRYPTED:
                if (settings.getIsAppendingDateTimeToBackups()) {
                    return String.format(Constants.BACKUP_FILENAME_CRYPT_FORMAT, Tools.getDateTimeString());
                } else {
                    return Constants.BACKUP_FILENAME_CRYPT;
                }
            case OPEN_PGP:
                if (settings.getIsAppendingDateTimeToBackups()) {
                    return String.format(Constants.BACKUP_FILENAME_PGP_FORMAT, Tools.getDateTimeString());
                } else {
                    return Constants.BACKUP_FILENAME_PGP;
                }
        }

        return Constants.BACKUP_FILENAME_PLAIN;
    }
}

